MATCH (m:Method)-[:DEFINES_METHOD|DEFINES*1..2]->(c:Class)
WHERE m.name CONTAINS 'ConfigMapping' OR c.name CONTAINS 'ConfigMapping'
RETURN c.name AS class_name, m.name AS method_name, m.file AS file_path
ORDER BY class_name, method_name
LIMIT 30
