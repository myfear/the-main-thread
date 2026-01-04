package com.vibecheck.tokenizer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.enterprise.context.ApplicationScoped;
import com.vibecheck.tokenizer.TokenizerType.Type;

@ApplicationScoped
@TokenizerType(Type.SIMPLE)
public class SimpleRobertaTokenizer implements Tokenizer {

    private static final int CLS = 0;
    private static final int PAD = 1;
    private static final int SEP = 2;

    private final Map<String, Integer> vocab;
    private final Map<String, Integer> bpeRanks;

    private static final Pattern TOKEN_PATTERN = Pattern
            .compile("'s|'t|'re|'ve|'m|'ll|'d|\\p{L}+|\\p{N}+|[^\\s\\p{L}\\p{N}]+|\\s+");

    public SimpleRobertaTokenizer() throws IOException {
        this.vocab = loadVocab();
        this.bpeRanks = loadBpeRanks();
    }

    @Override
    public long[] encode(String text, int maxLength) {
        List<Integer> tokenIds = new ArrayList<>();
        tokenIds.add(CLS);

        List<String> tokens = tokenize(text);
        List<String> bpeTokens = bpe(tokens);

        for (String token : bpeTokens) {
            tokenIds.add(vocab.getOrDefault(token, vocab.get("<unk>")));
        }

        tokenIds.add(SEP);

        if (tokenIds.size() > maxLength) {
            tokenIds = tokenIds.subList(0, maxLength);
            tokenIds.set(maxLength - 1, SEP);
        }

        while (tokenIds.size() < maxLength) {
            tokenIds.add(PAD);
        }

        return tokenIds.stream().mapToLong(Integer::longValue).toArray();
    }

    private List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        var matcher = TOKEN_PATTERN.matcher(text);
        while (matcher.find()) {
            tokens.add(matcher.group());
        }
        return tokens;
    }

    private List<String> bpe(List<String> tokens) {
        List<String> result = new ArrayList<>();

        for (String token : tokens) {
            if (token.trim().isEmpty())
                continue;

            List<String> word = new ArrayList<>();
            for (char c : token.toCharArray()) {
                word.add(String.valueOf(c));
            }

            while (word.size() > 1) {
                String bestPair = null;
                int bestRank = Integer.MAX_VALUE;

                for (int i = 0; i < word.size() - 1; i++) {
                    String pair = word.get(i) + " " + word.get(i + 1);
                    Integer rank = bpeRanks.get(pair);
                    if (rank != null && rank < bestRank) {
                        bestRank = rank;
                        bestPair = pair;
                    }
                }

                if (bestPair == null)
                    break;

                String[] parts = bestPair.split(" ");
                List<String> newWord = new ArrayList<>();
                int i = 0;

                while (i < word.size()) {
                    if (i < word.size() - 1 &&
                            word.get(i).equals(parts[0]) &&
                            word.get(i + 1).equals(parts[1])) {
                        newWord.add(parts[0] + parts[1]);
                        i += 2;
                    } else {
                        newWord.add(word.get(i));
                        i++;
                    }
                }

                word = newWord;
            }

            result.addAll(word);
        }

        return result;
    }

    private Map<String, Integer> loadVocab() throws IOException {
        var json = Files.readString(
                Path.of("src/main/resources/model/vocab.json"),
                StandardCharsets.UTF_8);
        return new ObjectMapper().readValue(json, Map.class);
    }

    private Map<String, Integer> loadBpeRanks() throws IOException {
        var lines = Files.readAllLines(
                Path.of("src/main/resources/model/merges.txt"),
                StandardCharsets.UTF_8);
        Map<String, Integer> ranks = new HashMap<>();
        int rank = 0;
        for (String line : lines) {
            if (line.startsWith("#"))
                continue;
            ranks.put(line, rank++);
        }
        return ranks;
    }
}