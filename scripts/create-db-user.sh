#!/bin/bash
# Creates (or re-keys) the least-privilege MySQL user "gymapp" for the gymdb schema and writes
# its random password into local.properties without printing it.
#
# Usage (from the project root): bash scripts/create-db-user.sh
set -euo pipefail
cd "$(dirname "$0")/.."

MYSQL=$(command -v mysql || true)
if [ -z "$MYSQL" ]; then
  for candidate in /usr/local/mysql/bin/mysql /opt/homebrew/opt/mysql@8.4/bin/mysql /opt/homebrew/bin/mysql; do
    if [ -x "$candidate" ]; then
      MYSQL=$candidate
      break
    fi
  done
fi
if [ -z "$MYSQL" ]; then
  echo "Could not find the mysql program. Is MySQL installed?"
  exit 1
fi
if [ ! -f local.properties ]; then
  echo "local.properties is missing. Copy local.properties.example first."
  exit 1
fi

# Prefix/suffix satisfy MySQL's validate_password policy if it is enabled.
PASSWORD="Gym_$(openssl rand -hex 24)_9"

CNF=$(mktemp)
TMP=$(mktemp)
trap 'rm -f "$CNF" "$TMP"' EXIT
chmod 600 "$CNF" "$TMP"

# Passwords are passed via stdin / a temp option file, never as command-line arguments.
echo "MySQL ROOT password:"
"$MYSQL" -u root -p <<SQL
CREATE USER IF NOT EXISTS 'gymapp'@'localhost' IDENTIFIED BY '$PASSWORD';
ALTER USER 'gymapp'@'localhost' IDENTIFIED BY '$PASSWORD';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES ON gymdb.* TO 'gymapp'@'localhost';
SQL

printf '[client]\nuser=gymapp\npassword="%s"\nhost=127.0.0.1\n' "$PASSWORD" > "$CNF"
"$MYSQL" --defaults-extra-file="$CNF" gymdb -e "SELECT CURRENT_USER() AS logged_in_as, DATABASE() AS db;"

PW="$PASSWORD" awk '
  /^# The root password you chose when installing MySQL/ {
    print "# Password of the MySQL user gymapp (made by scripts/create-db-user.sh)"; next
  }
  /^spring\.datasource\.password=/ { print "spring.datasource.password=" ENVIRON["PW"]; found = 1; next }
  { print }
  END { if (!found) print "spring.datasource.password=" ENVIRON["PW"] }
' local.properties > "$TMP"
cat "$TMP" > local.properties   # keeps the original file permissions

echo
echo "Done. The backend now logs in to MySQL as gymapp (access to gymdb only)."
echo "Restart the backend to use the new password."
