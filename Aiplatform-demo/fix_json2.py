import json

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

# Parse first JSON
data1 = json.loads(first_json)

# The remaining content starts with ',' then has more items
remaining_stripped = remaining.strip()
if remaining_stripped.startswith(','):
    remaining_stripped = remaining_stripped[1:].strip()

# Now find where the second JSON object ends
brace_count2 = 0
bracket_count2 = 0
in_string2 = False
escape_next2 = False
json_end2 = 0

for i, c in enumerate(remaining_stripped):
    if escape_next2:
        escape_next2 = False
        continue
    if c == '\\':
        escape_next2 = True
        continue
    if c == '"':
        in_string2 = not in_string2
        continue
    if in_string2:
        continue
    if c == '{':
        brace_count2 += 1
    elif c == '}':
        brace_count2 -= 1
    elif c == '[':
        bracket_count2 += 1
    elif c == ']':
        bracket_count2 -= 1
    
    if brace_count2 == 0 and bracket_count2 == 0 and i > 100:
        json_end2 = i + 1
        break

second_json = remaining_stripped[:json_end2]
data2 = json.loads(second_json)

print(f'First JSON has {len(data1["item"])} items')
print(f'Second JSON has {len(data2["item"])} items')

# Merge items
data1['item'].extend(data2['item'])

# Write back
with open('API_TEST.postman_collection.json', 'w') as f:
    json.dump(data1, f, indent=2, ensure_ascii=False)

print(f'Total items after merge: {len(data1["item"])}')
print('File fixed successfully!')
