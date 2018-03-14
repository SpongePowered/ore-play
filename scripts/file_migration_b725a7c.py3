# File path fixing for Ore
# Run fix_file_paths_ecd8100.py3 first
#
# b725a7cce20920dbc07530ac5b60569f3ba54ebe changed the
# version directories. This script will move the files
# into separate directories.
#
# by @phase, @progwml6, & @felixoi

import psycopg2
import zipfile
import json
import os.path

# variables
dbname = "ore"
user = "postgres"
host = "localhost"
password = "password"
main_directory = "/home/ore/files/plugins/" # with slash at the end

# from https://stackoverflow.com/questions/7829499/using-hashlib-to-compute-md5-digest-of-a-file-in-python-3
def md5sum(filename):
    with open(filename, mode='rb') as f:
        d = hashlib.md5()
        for buf in iter(partial(f.read, 128), b''):
            d.update(buf)
    return d.hexdigest()

# Connec to the DB
connection = psycopg2.connect(dbname=dbname, user=user, host=host, password=password)
cursor = connection.cursor()

old_project_names = {}

cursor.execute("""SELECT project_id, file_name, signature_file_name, version_string, hash, id FROM project_versions ORDER BY id""")
project_versions = cursor.fetchall()

for project_version in project_versions:
  project_id = project_version[0]
  file_name = project_version[1]
  signature_file_name = project_version[2]
  version_string = project_version[3]
  hash = project_version[4]
  version_id = project_version[5]

  cursor.execute("""SELECT name, owner_id FROM projects WHERE id = """ + str(project_id))
  project = cursor.fetchall()[0]
  project_name = project[0]
  owner_id = project[1]

  cursor.execute("""SELECT name FROM users WHERE id = """ + str(owner_id))
  user = cursor.fetchall()[0]

  old_folder = main_directory + user[0] + "/" + project_name + "/"
  new_folder = main_directory + user[0] + "/" + project_name + "/versions/" + version_name + "/"

  old_file_path = old_folder + file_name
  new_file_path = new_folder + file_name

  old_sig_path = old_folder + signature_file_name
  new_sig_path = new_folder + signature_file_name

  if os.path.isfile(old_file_path):
    old_file_hash = md5sum(old_file_path)
    if old_file_hash == hash:
      if not os.path.exists(new_folder):
        print("Making Directory: " + new_folder)
        os.makedirs(new_folder)
      # Move Plugin
      print("Moving " + old_file_path + " to " + new_file_path)
      os.rename(old_file_path, new_file_path)
      if os.path.isfile(old_sig_path):
        # Move Sig
        print("Moving signature " + old_sig_path + " to " + new_sig_path)
        os.rename(old_sig_path, new_sig_path)
    else:
      # This probably means someone uploaded a version that conflicted with another
      # version and it got overwritten. I shouldn't be deleted because it might match
      # another version in the DB
      print("ERROR: " + old_file_path + " doesn't have the hash " + hash)
      cursor.execute("""DELETE FROM project_versions WHERE id = """ + version_id)
      cursor.execute("""SELECT id, version_string FROM project_versions WHERE project_id = """ + project_id)
      results = cursor.fetchall()
      if results:
        # Set the recommended version of the project
        new_project_version = results[0]
        new_project_version_id = new_project_version[0]
        new_project_version_name = new_project_version[1]
        print("Setting the Recommended Version for " + project_name + " to " + new_project_version_name)
        cursor.execute("UPDATE projects SET recommended_version = " + new_project_version_id + " WHERE id = " + project_id)
      else:
        # No versions exist for this project
        print("No versions exists for " + project_name + ". (This probably shouldn't happen.)")
        user_input = "no"
        while True:
          user_input = input("Delete " + project_name + "? [yes/no] ")
          if user_input in ["yes", "no"]:
            break
          else:
            print("That is not a valid response!")
        if user_input == "yes":
          print("Deleted " + project_name)
          cursor.execute("DELETE FROM projects WHERE id = " + project_id)
        elif user_input == "no":
          print("Setting the Recommended Version ID to -1")
          cursor.execute("UPDATE projects SET recommended_version = -1 WHERE id = " + project_id)


cursor.close()
connection.close()
