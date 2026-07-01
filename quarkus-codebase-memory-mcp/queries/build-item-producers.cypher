MATCH (c:Class)-[:DEFINES_METHOD]->(m:Method)
WHERE c.name IN [
  'ConfigGenerationBuildStep',
  'ConfigMappingProcessor'
]
RETURN c.name AS class_name, m.name AS method_name, m.file AS file_path
ORDER BY class_name, method_name
