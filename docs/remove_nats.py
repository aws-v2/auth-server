import re
import os

file_path = r"c:\Users\0x6D617274696E\Downloads\auth-server (3)\auth-server\docs\whimsical-nibbling-ember.md"

with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

# 1. Remove " via NATS request-reply" (already done? check idempotency)
content = content.replace(" via NATS request-reply", "")

# 2. Remove " with NATS Integration" from title
content = content.replace(" with NATS Integration", "")

# 3. Remove the section.
# We'll try to find the line starting with "3." and containing "NATS Integration"
# Then remove lines until we hit a blank line or start of next section or end of bullets.
lines = content.split('\n')
new_lines = []
skip = False
for line in lines:
    if "3." in line and "NATS Integration" in line:
        print(f"Found section start: {repr(line)}")
        skip = True
        continue
    
    if skip:
        # Stop skipping if we hit an empty line followed by "---" or next section?
        # The structure is:
        # [bullets]
        # [empty line]
        # ---
        if line.strip() == "---":
            skip = False
        elif line.strip() == "":
            pass # keep skipping empty lines in the block
        # Check if it's a bullet
        elif line.strip().startswith("-"):
            print(f"Skipping bullet: {repr(line)}")
            pass # skip
        else:
            # Maybe end of section?
            # If we hit text that isn't a bullet and isn't empty, we stop skipping?
            # In the file, there is "---" after the bullets.
            # But there might be an empty line before "---".
            print(f"Stopping skip at: {repr(line)}")
            skip = False

    if not skip:
        new_lines.append(line)

content = '\n'.join(new_lines)

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)

print("Done.")
