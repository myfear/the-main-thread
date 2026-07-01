# review-standards

Use this skill when reviewing authentication code.

Look for password hashing problems. Password storage should not use fast hashes
such as SHA-1 or MD5.

Look for token comparison problems. Reset tokens, API keys, and similar secrets
should use constant-time comparison so timing differences do not leak useful
information.

Respect what the user asked for. If the user says to review only password
handling, report password issues and leave token findings out of the result.
