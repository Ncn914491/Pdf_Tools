import re

file_path = 'app/src/main/java/com/yourname/pdftoolkit/ui/MainActivity.kt'

with open(file_path, 'r') as f:
    content = f.read()

# Check for runBlocking inside ACTION_VIEW/SEND block
pattern = r'Intent\.ACTION_VIEW, Intent\.ACTION_SEND -> \{.*?runBlocking \{.*?copyToCacheSynchronous'
match = re.search(pattern, content, re.DOTALL)

if match:
    print("FOUND: Blocking call detected in ACTION_VIEW/SEND handler")
else:
    print("NOT FOUND: Blocking call not detected in ACTION_VIEW/SEND handler")
