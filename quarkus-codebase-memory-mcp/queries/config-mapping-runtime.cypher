MATCH (m:Method)
WHERE m.name CONTAINS 'getConfigMapping'
   OR m.name CONTAINS 'create'
RETURN m.name AS method_name, m.file AS file_path
ORDER BY method_name
LIMIT 20
