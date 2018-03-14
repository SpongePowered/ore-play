# File path fixing for Ore
#
# Ore before ecd8100420e9095500ff409ac738948b25c3eed6 placed
# some versions in an incorrect location. This script will move
# those files to the location they should be. Plugins that don't
# contain an mcmod.info file can't be moved.
#
# Dependencies: psycopg2
#
# by @phase

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

def plugin_metadata(path):
  jar = zipfile.ZipFile(path, 'r')
  mcmodinfo = jar.read("mcmod.info")
  data = json.loads(mcmodinfo.decode("utf-8"))[0]
  return data

# Connec to the DB
connection = psycopg2.connect(dbname=dbname, user=user, host=host, password=password)
cursor = connection.cursor()

old_project_names = {}

cursor.execute("""SELECT project_id, file_name, signature_file_name FROM project_versions ORDER BY id""")
project_versions = cursor.fetchall()

for project_version in project_versions:
  project_id = project_version[0]
  file_name = project_version[1]
  signature_file_name = project_version[2]

  cursor.execute("""SELECT name, owner_id FROM projects WHERE id = """ + str(project_id))
  project = cursor.fetchall()[0]
  project_name = project[0]

  cursor.execute("""SELECT name FROM users WHERE id = """ + str(project[1]))
  user = cursor.fetchall()[0]

  if project_id in old_project_names:
    old_name = old_project_names[project_id]
    new_name = project[0]

    old_folder = main_directory + user[0] + "/" + old_name + "/"
    new_folder = main_directory + user[0] + "/" + new_name + "/"

    if old_name != new_name:
      old_file_path = old_folder + file_name
      new_file_path = old_folder + file_name
      if os.path.isfile(old_file_path):
        print("Old: " + old_file_path + "\nNew: " + new_file_path)
        os.rename(old_file_path, new_file_path)

        old_sig_path = old_folder + signature_file_name
        new_sig_path = new_folder + signature_file_name

        if os.path.isfile(old_sig_path):
          print("Old sig: " + old_sig_path + "\nNew sig: " + new_sig_path)
          os.rename(old_sig_path, new_sig_path)
  else:
    print("Caching project id: " + str(project_id) + " (" + project[0] + ")")
    file = main_directory + user[0] + "/" + project_name + "/" + file_name
    try:
      metadata = plugin_metadata(file)
      old_project_names[project_id] = metadata["name"]
    except:
      print("Project " + file + " (" + str(project_id) + " " + project_name + ") doesn't contain mcmod.info")

cursor.close()
connection.close()
