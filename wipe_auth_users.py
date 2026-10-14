import os
import glob
import sys

def find_service_account():
    # 1. Check current directory
    candidates = glob.glob("*.json")
    for f in candidates:
        if "firebase-adminsdk" in f or "serviceAccount" in f:
            return os.path.abspath(f)
    
    # 2. Check user Downloads folder
    downloads = os.path.expanduser(r"~\Downloads")
    if os.path.exists(downloads):
        candidates = glob.glob(os.path.join(downloads, "*.json"))
        # Sort by most recently created
        candidates.sort(key=os.path.getmtime, reverse=True)
        for f in candidates:
            if "firebase-adminsdk" in f or "gabai" in f or "serviceAccount" in f:
                return os.path.abspath(f)
                
    return None

def wipe_all_users(key_path):
    import firebase_admin
    from firebase_admin import credentials, auth

    print(f"[*] Initializing Firebase Admin with: {key_path}")
    cred = credentials.Certificate(key_path)
    app = firebase_admin.initialize_app(cred)

    total_deleted = 0
    print("[*] Fetching users from Firebase Authentication...")
    
    page = auth.list_users()
    while page:
        uids = [user.uid for user in page.users]
        if not uids:
            break
        print(f"[*] Deleting batch of {len(uids)} users...")
        result = auth.delete_users(uids)
        total_deleted += result.success_count
        if result.failure_count > 0:
            print(f"[!] Warning: {result.failure_count} users failed to delete.")
            for err in result.errors:
                print(f"    - Index {err.index}: {err.reason}")
        page = page.get_next_page()

    print(f"\n[SUCCESS] Completed! Total users wiped from Authentication: {total_deleted}")

if __name__ == "__main__":
    key = find_service_account()
    if not key:
        print("[!] No Service Account JSON key found yet.")
        print("Please download your Service Account key from Firebase Console:")
        print("Project Settings (Gear icon) -> Service accounts -> 'Generate new private key'")
        sys.exit(1)
    wipe_all_users(key)
