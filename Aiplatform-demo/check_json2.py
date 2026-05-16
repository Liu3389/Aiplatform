import json
import sys

with open('API_TEST.postman_collection.json') as f:
    content = f.read()

# Find the first complete JSON object
brace_count = 0
bracket_count = 0
in_string = False
escape_next = False
json_end = 0

for i, c in enumerate(content):
    if escape_next:
        escape_next = False
        continue
    if c == '\\':
        escape_next = True
        continue
    if c == '"':
        in_string = not in_string
        continue
    if in_string:
        continue
    if c == '{':
        brace_count += 1
    elif c == '}':
        brace_count -= 1
    elif c == '[':
        bracket_count += 1
    elif c == ']':
        bracket_count -= 1
    
    if brace_count == 0 and bracket_count == 0 and i > 100:
        json_end = i + 1
        break

# Extract first JSON object
first_json = content[:json_end]
remaining = content[json_end:]

print(f'First JSON object ends at position: {json_end}')
print(f'Remaining content length: {len(remaining)}')
print(f'Remaining content (first 200 chars): {repr(remaining[:200])}')

# Try to parse first JSON
try:
    data = json.loads(first_json)
    print('\nFirst JSON parsed successfully!')
    print(f'Has "info": {"info" in data}')
    print(f'Has "item": {"item" in data}')
    if "item" in data:
        print(f'Number of top-level items: {len(data["item"])}')
except json.JSONDecodeError as e:
    print(f'\nFirst JSON parse error: {e}')
