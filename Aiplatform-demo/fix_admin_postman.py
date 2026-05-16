import json

with open('ADMIN_API.postman_collection.json') as f:
    data = json.load(f)

def find_and_remove_duplicates(items, parent_name=''):
    """Find and remove duplicate endpoints and misplaced user endpoints"""
    seen_urls = set()
    to_remove = []
    
    for i, item in enumerate(items):
        if 'request' in item:
            url = item['request']['url']['raw']
            method = item['request']['method']
            key = f'{method} {url}'
            
            # Check for duplicates
            if key in seen_urls:
                to_remove.append(i)
                print(f'  Removing duplicate: {key}')
                continue
            seen_urls.add(key)
            
            # Check for misplaced user endpoints (should not be in ADMIN_API)
            if '/api/user/' in url and '/api/user/recharge' not in url:
                # Only remove user endpoints that are clearly not admin-related
                if 'my-coupons' in url or 'coupon-usage-records' in url or 'recharge-with-coupon' in url:
                    to_remove.append(i)
                    print(f'  Removing misplaced user endpoint: {key}')
                    continue
        
        if 'item' in item:
            # Recursively process sub-items
            find_and_remove_duplicates(item['item'], item.get('name', ''))
    
    # Remove items in reverse order to maintain indices
    for i in reversed(to_remove):
        items.pop(i)

print('Processing ADMIN_API.postman_collection.json...')
find_and_remove_duplicates(data['item'])

# Count total interfaces
def count_interfaces(items):
    count = 0
    for item in items:
        if 'request' in item:
            count += 1
        if 'item' in item:
            count += count_interfaces(item['item'])
    return count

total = count_interfaces(data['item'])
print(f'\nTotal interfaces after cleanup: {total}')

# Update description
old_desc = data['info']['description']
# Update the total count
import re
new_desc = re.sub(r'\*\*总计\*\*: \d+个API接口', f'**总计**: {total}个API接口', old_desc)
data['info']['description'] = new_desc

# Write back
with open('ADMIN_API.postman_collection.json', 'w') as f:
    json.dump(data, f, indent=2, ensure_ascii=False)

print('File updated successfully!')
