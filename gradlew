#!/bin/sh

# Remember: this file must have UNIX line endings (LF), not Windows (CRLF)

#... [very long script - I'll provide the essential parts]
# For now, use this minimal version:

#!/bin/sh
# Gradle startup script for UNIX systems

exec java -jar gradle/wrapper/gradle-wrapper.jar "$@"
