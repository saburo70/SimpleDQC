/*
$CODE=DQ002
$DESCRIPTION=Users with short names
*/
SELECT 
    CAST(id AS CHAR) as issueKey,
    name,
    email
FROM users 
WHERE LENGTH(name) < 5
