import os
from PIL import Image

source_icon = "/home/kamel/Téléchargements/symbole-doutil-radio-noir.png"
base_path = "app/src/main/res"

sizes = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192
}

if not os.path.exists(source_icon):
    print(f"Source icon not found: {source_icon}")
    exit(1)

img = Image.open(source_icon)

for folder, size in sizes.items():
    target_dir = os.path.join(base_path, folder)
    if not os.path.exists(target_dir):
        os.makedirs(target_dir)
    
    target_path = os.path.join(target_dir, "ic_launcher.png")
    # Resize with high quality
    resized_img = img.resize((size, size), Image.Resampling.LANCZOS)
    resized_img.save(target_path)
    print(f"Saved {target_path} ({size}x{size})")

# Also update round icon if it exists
for folder, size in sizes.items():
    target_path = os.path.join(base_path, folder, "ic_launcher_round.png")
    # For now, just copy the same icon
    img.resize((size, size), Image.Resampling.LANCZOS).save(target_path)
    print(f"Saved {target_path}")

print("Icon update complete!")
