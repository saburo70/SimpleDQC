/*
$CODE=DQ001
$DESCRIPTION=Users without email
*/
SELECT 
    CAST(id AS CHAR) as issueKey,
    name
FROM users 
WHERE email IS NULL OR email = ''
