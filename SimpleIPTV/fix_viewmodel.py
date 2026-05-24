import re

with open('app/src/main/java/com/example/simpleiptv/ui/viewmodel/MainViewModel.kt', 'r') as f:
    text = f.read()

text = re.sub(
    r'var\s+uiState:\s*MainUiState\s+by\s+mutableStateOf\(MainUiState\(\)\)',
    r'val uiState: MainUiState = MainUiState()',
    text
)

text = re.sub(
    r'private\s+inline\s+fun\s+updateUiState\s*\(\s*transform:\s*MainUiState\.\(\)\s*->\s*MainUiState\s*\)\s*\{(.*?)\}',
    r'private inline fun updateUiState(block: MainUiState.() -> Unit) {\n        uiState.block()\n    }',
    text,
    flags=re.DOTALL
)

def fix_copy_block(block):
    # block is something like "copy(a=b, c=d)"
    # we need to remove "copy(" and the last ")", and replace the top-level commas with newlines/semicolons
    if not block.startswith("copy("): return block
    inner = block[5:-1]
    
    # We parse the inner text, keeping track of parenthesis depth to only split on commas at depth 0
    depth = 0
    in_str = False
    result = []
    current = []
    
    # Actually wait, `dialogState = dialogState.copy(...)` does contain commas inside. 
    for char in inner:
        if char == '"':
            in_str = not in_str
            current.append(char)
        elif char == '(' and not in_str:
            depth += 1
            current.append(char)
        elif char == ')' and not in_str:
            depth -= 1
            current.append(char)
        elif char == ',' and not in_str and depth == 0:
            result.append("".join(current).strip())
            current = []
        else:
            current.append(char)
            
    if current:
        result.append("".join(current).strip())
        
    return "\n            ".join(result)

# Find all blocks of `updateUiState { ... }`
# Because `updateUiState` blocks might contain `copy( ... )`
# We use regex to find `updateUiState { copy(` first
pattern = re.compile(r'updateUiState\s*\{\s*copy\((.*?)\)\s*\}', re.DOTALL)

def replacer(match):
    inner = match.group(1)
    
    depth = 0
    result = []
    current = []
    
    for char in inner:
        if char == '(': depth += 1
        elif char == ')': depth -= 1
        
        if char == ',' and depth == 0:
            result.append("".join(current).strip())
            current = []
        else:
            current.append(char)
    if current:
        result.append("".join(current).strip())
        
    assignments = "\n                ".join(result)
    return f"updateUiState {{\n                {assignments}\n            }}"

new_text = pattern.sub(replacer, text)

with open('app/src/main/java/com/example/simpleiptv/ui/viewmodel/MainViewModel.kt', 'w') as f:
    f.write(new_text)

