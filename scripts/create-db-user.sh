#!/bin/bash
# ------------------------------------------------------------------
#  Creates the MySQL user "gymapp" that the backend uses instead of root.
#
#  Why: root can do anything to every database on your Mac. If someone ever
#  tricked the backend into running their SQL, root would let them do all of it.
#  gymapp can only read and change the gymdb database, and nothing else.
#
#  Run it from the project folder (where pom.xml is):
#      bash scripts/create-db-user.sh
#
#  It asks for your MySQL ROOT password once (nothing shows while you type).
#  The new gymapp password is random, goes straight into local.properties,
#  and is never printed. Safe to run again: gymapp just gets a new password.
# ------------------------------------------------------------------
set -euo pipefail
cd "$(dirname "$0")/.."   # go to the project folder, wherever you ran this from

# 1. Find the mysql program (the DMG installer puts it in /usr/local/mysql/bin)
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

# 2. A random password. openssl ships with macOS; 24 random bytes = 48 hex characters.
#    "Gym_" and "_9" make sure it has an uppercase letter, a digit and a symbol,
#    in case MySQL's password-strength check is switched on.
PASSWORD="Gym_$(openssl rand -hex 24)_9"

# Temporary files are deleted when the script ends, even if it fails
CNF=$(mktemp)
TMP=$(mktemp)
trap 'rm -f "$CNF" "$TMP"' EXIT
chmod 600 "$CNF" "$TMP"

# 3. Create gymapp and give it rights on gymdb only.
#    The SQL goes in through stdin (the <<SQL block), so the password never
#    appears on a command line where other programs could see it.
echo "MySQL ROOT password:"
"$MYSQL" -u root -p <<SQL
CREATE USER IF NOT EXISTS 'gymapp'@'localhost' IDENTIFIED BY '$PASSWORD';
ALTER USER 'gymapp'@'localhost' IDENTIFIED BY '$PASSWORD';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES ON gymdb.* TO 'gymapp'@'localhost';
SQL

# 4. Check that gymapp can log in the same way Java does (TCP to 127.0.0.1).
#    The password goes in a private temporary file, not on the command line.
printf '[client]\nuser=gymapp\npassword="%s"\nhost=127.0.0.1\n' "$PASSWORD" > "$CNF"
"$MYSQL" --defaults-extra-file="$CNF" gymdb -e "SELECT CURRENT_USER() AS logged_in_as, DATABASE() AS db;"

# 5. Put the new password in local.properties (replaces the old root password line).
#    The password reaches awk through an environment variable (PW), not the command line.
PW="$PASSWORD" awk '
  /^# The root password you chose when installing MySQL/ {
    print "# Password of the MySQL user gymapp (made by scripts/create-db-user.sh)"; next
  }
  /^spring\.datasource\.password=/ { print "spring.datasource.password=" ENVIRON["PW"]; found = 1; next }
  { print }
  END { if (!found) print "spring.datasource.password=" ENVIRON["PW"] }
' local.properties > "$TMP"
cat "$TMP" > local.properties   # "cat >" keeps the file's permissions

echo
echo "Done. The backend now logs in to MySQL as gymapp (access to gymdb only)."
echo "Restart it in NetBeans. Workbench can still use root as before."
