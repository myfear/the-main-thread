package com.ibm.developer.shieldstral.policy;

interface PolicyClassifier {

    ClassifierScore classify(ClassifierRequest request);
}
