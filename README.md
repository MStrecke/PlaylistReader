# Introduction

This app can be used to work around the insufficiencies of the playlist import functionality in some Android implementations.

If you place a media file (a music file in this context) onto your Android device the system will 
scan it and it will store meta data (e.g. the file path) into an internal database called the `MediaStore`.

Similarly, if you place a playlist into the `Playlist` folder of the internal storage in your device
the system *should* import and store it into that database.

Experience shows that this works on some systems and not on others. In some cases strange things 
happen when it tries.

# Playlists

In essence a playlist is a file with a list of filenames pointing to **existing** songs on the device.
The name of the playlist file determines the name of the playlist on the device.

# How do I import a playlist using this app?

- Select the text file containing the playlist.
- The app reads it line by line ignoring any line starting with "#".
- It then tries to match the lines' content with the file paths in the `MediaStore` database. If it 
  finds a match the file will be added to the playlist.
  - For this to work the playlist file must contain absolute file paths.
  
# How do I know which file paths to use?

The `export` button lets you create a text file with the absolute file path of all audio 
entries in the database.  You can use this as a base for creating playlists externally.

# Notes

## Known issues

In newer Android versions you will be prompted to grant access permissions to the app when you
first trigger an action by the app i.e. pressing one of the buttons.  This only happens once and
you have to press the button again to execute the action. 

## Newer Android versions

- This app has currently been tested only on a limited number of devices with Android 8.1 and below.
- It will most likely not work correctly on Android 10 and above due to changes in the Android 
  Storage permissions structure.  However, devices with Android 10 seem to handle playlists 
  correctly on their own.

## Exact absolute filepaths of existing files

- This utility depends on the fact that the filepath in the playlist and the filepath in the 
  database match *excatly*.  Be aware that a file might be reachable by different paths 
  (e.g. `/storage/emulated/0/xxx` and `/sdcard/xxx` or perhaps `/storage/0/xxx`).  Use the 
  export function to find out which one to use.
- If the playlist text file contains a path that has no match in the database this entry will be ignored.
- Moving files within the filesystem will change its filepath. Ths `MediaStore` will be updated 
  automatically.  The entry in your playlist text file has to be edited manually.

## M3U playlists

Some music players - like the `Retro Music Player` - let you export M3U playlists (with absolute 
file paths) for backup purposes. This app can read those files (it will ignore lines starting with "#")

 