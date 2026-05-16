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
# We need to extract the items array from the remaining content
# The remaining should be: , { "name": "...", "item": [...] }, ... ]}
# So we need to wrap it in a proper structure

# Find where the items array starts in remaining
# It should start after the first closing brace
remaining_stripped = remaining.strip()
if remaining_stripped.startswith(','):
    remaining_stripped = remaining_stripped[1:].strip()

# Now we have: { "name": "...", "item": [...] }, ... ]}
# We need to wrap this in a proper items array
# Find the last ]} which should be the end
# Extract just the items

# Actually, let's parse the remaining as a JSON array of items
# by wrapping it properly
try:
    # Try to parse as array of items
    items_json = '[' + remaining_stripped
    items_data = json.loads(items_json)
    print(f'Successfully parsed {len(items_data)} additional items')
    
    # Add to first data
    data1['item'].extend(items_data)
    
    # Write back
    with open('API_TEST.postman_collection.json', 'w') as f:
        json.dump(data1, f, indent=2, ensure_ascii=False)
    
    print(f'Total items after merge: {len(data1["item"])}')
    print('File fixed successfully!')
except json.JSONDecodeError as e:
    print(f'Parse error: {e}')
    print(f'Remaining content (first 500 chars): {remaining_stripped[:500]}')
