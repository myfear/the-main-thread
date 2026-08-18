package com.ibm.developer.shieldstral.policy;

record ClassifierRequest(String instruction, String query, String document) {

    String userMessage() {
        return """
                <Instruct>: %s

                <Query>: %s

                <Document>: %s
                """.formatted(instruction, query, document);
    }
}
