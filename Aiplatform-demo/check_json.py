import json
import sys

with open('API_TEST.postman_collection.json') as f:
    content = f.read()

# Try to find where JSON ends
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
        json_end = i
        break

print(f'JSON ends at position: {json_end}')
print(f'Total content length: {len(content)}')
print(f'Extra data starts at: {json_end+1}')
print(f'Extra data: {repr(content[json_end:json_end+100])}')
