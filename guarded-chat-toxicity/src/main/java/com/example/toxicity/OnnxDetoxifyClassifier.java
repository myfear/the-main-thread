package com.example.toxicity;

import java.nio.FloatBuffer;
import java.nio.LongBuffer;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import io.quarkus.logging.Log;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class OnnxDetoxifyClassifier implements ToxicityClassifier {

    @ConfigProperty(name = "toxicity.model.path")
    String modelPath;

    @ConfigProperty(name = "toxicity.tokenizer.path")
    String tokenizerPath;

    private OrtEnvironment env;
    private OrtSession session;
    private HuggingFaceTokenizer tokenizer;

    private static final String[] LABELS = {
            "toxicity",
            "severe_toxicity",
            "obscene",
            "identity_attack",
            "insult",
            "threat",
            "sexual_explicit",
            "male",
            "female",
            "homosexual_gay_or_lesbian",
            "christian",
            "jewish",
            "muslim",
            "black",
            "white",
            "psychiatric_or_mental_illness"
    };

    @PostConstruct
    void init() {
        try {
            this.env = OrtEnvironment.getEnvironment();
            this.session = env.createSession(Path.of(modelPath).toAbsolutePath().toString(),
                    new OrtSession.SessionOptions());

            // Create options map to configure the tokenizer
            Map<String, String> options = new HashMap<>();
            options.put("truncation", "true");
            options.put("padding", "true");

            // HuggingFaceTokenizer automatically discovers tokenizer_config.json
            // when tokenizer.json is in the same directory
            Path tokenizerPathObj = Path.of(tokenizerPath);
            // If tokenizerPath points to a file, use its parent directory so it can find
            // all tokenizer files
            Path tokenizerDir = tokenizerPathObj.toFile().isFile()
                    ? tokenizerPathObj.getParent()
                    : tokenizerPathObj;

            this.tokenizer = HuggingFaceTokenizer.newInstance(tokenizerDir, options);

            Log.info("Detoxify ONNX classifier initialized.");
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize Detoxify ONNX classifier", e);
        }
    }

    @Override
    public ToxicityScores predict(String text) {
        try {
            // Tokenize the input text into token IDs and attention mask using the
            // configured tokenizer.
            Encoding encoding = tokenizer.encode(text);

            // Extract the token IDs (input_ids) that represent the text as a sequence of
            // integers.
            long[] inputIds = toLongArray(encoding.getIds());
            // Extract the attention mask that indicates which tokens are real (1) vs
            // padding (0).
            long[] attentionMask = toLongArray(encoding.getAttentionMask());

            // Define the tensor shape as [batch_size=1, sequence_length] for the model
            // input.
            long[] shape = new long[] { 1, inputIds.length };

            // Create ONNX tensors from the input arrays and run model inference.
            try (OnnxTensor inputIdsTensor = tensorOf(shape, inputIds);
                    OnnxTensor attentionMaskTensor = tensorOf(shape, attentionMask);
                    OrtSession.Result result = session.run(Map.of(
                            "input_ids", inputIdsTensor,
                            "attention_mask", attentionMaskTensor))) {

                // Extract the output tensor containing the raw logits from the model.
                OnnxTensor outputTensor = (OnnxTensor) result.get(0);
                // Get the float buffer from the output tensor for efficient buffer-based
                // access.
                FloatBuffer logitsBuffer = outputTensor.getFloatBuffer();
                // Apply sigmoid activation function to convert logits into probability scores.
                double[] probs = sigmoid(logitsBuffer);

                // Map each probability score to its corresponding toxicity label.
                Map<String, Double> scores = new LinkedHashMap<>();
                for (int i = 0; i < Math.min(LABELS.length, probs.length); i++) {
                    scores.put(LABELS[i], probs[i]);
                }

                // Return the toxicity scores wrapped in a ToxicityScores object.
                return new ToxicityScores(scores);
            }

        } catch (OrtException e) {
            throw new IllegalStateException("Failed to run toxicity inference", e);
        }
    }

    @Override
    public void warmup() {
        predict("warmup");
    }

    private OnnxTensor tensorOf(long[] shape, long[] values) throws OrtException {
        LongBuffer buffer = LongBuffer.wrap(values);
        return OnnxTensor.createTensor(env, buffer, shape);
    }

    private static long[] toLongArray(long[] source) {
        return source;
    }

    private static double[] sigmoid(FloatBuffer logits) {
        int length = logits.limit();
        double[] out = new double[length];
        for (int i = 0; i < length; i++) {
            double x = logits.get(i);
            out[i] = 1.0 / (1.0 + Math.exp(-x));
        }
        return out;
    }

    @PreDestroy
    void shutdown() {
        try {
            if (session != null)
                session.close();
            if (env != null)
                env.close();
        } catch (Exception e) {
            Log.warn("Error while shutting down ONNX resources", e);
        }
    }
}