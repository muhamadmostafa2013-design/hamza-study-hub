# Offline photo text review

- From the Android share sheet, send one image into Study Hub. Select Arabic for
  printed Arabic, or German/Latin for the original bundled ML Kit recognizer.
  Recognition runs on the phone. Edit the proposed text, especially page and
  exercise numbers, before saving it as unverified shared homework. If no clear
  text was found, type it manually or cancel.
- In the learning capture flow, the photo is saved before optional recognition.
  Dismissing the language picker or review keeps the photo without the raw OCR
  text. Edited text is attached only after explicit save.
- The app does not upload the image or download models during recognition. Mixed
  scripts are not guaranteed, and the Arabic model is for printed text, not
  reliable handwriting or numeric transcription.

## Build provenance

- Arabic engine: [Tesseract4Android standard 4.9.0](https://github.com/adaptech-cz/Tesseract4Android/releases/tag/4.9.0), Apache-2.0, with bundled native dependencies. Their license texts are packaged under `assets/third-party/arabic-ocr`.
- Arabic language data: [Tesseract tessdata_fast, immutable commit](https://github.com/tesseract-ocr/tessdata_fast/blob/65727574dfcd264acbb0c3e07860e4e9e9b22185/ara.traineddata), Apache-2.0. Gradle fetches and verifies SHA-256 `e3206d3dc87fd50c24a0fb9f01838615911d25168f4e64415244b67d2bb3e729` when preparing a clean build. The model and its license are bundled in the app.
- Latin engine: [bundled ML Kit Text Recognition](https://developers.google.com/ml-kit/vision/text-recognition/v2/android). No change to the Latin recognition model.

## Device verification before release

1. In airplane mode from first launch, share a synthetic printed Arabic image,
   run recognition, correct numbers, and check that only confirmed text saves.
2. Repeat with a synthetic German image and a rotated JPEG. Also try a blank or
   invalid image; no entry should save until text is entered and confirmed.
3. Share a synthetic text message, delete an unrelated line, and verify only your edits are saved. Cancel an import or save it, rotate the screen, and verify no replay. Rotate
   during camera capture or permission return; check that the attempt photo is
   preserved. Cancel or correct OCR text and confirm that the attempt remains.
4. Use a disposable test Android profile with no school account to verify local
   homework, checklist, photo and certificate availability in airplane mode.
5. For a production-candidate APK only, seed local test data in a fresh install
   of the previous signed build, update in place with same signer and higher
   version code, and verify all seeded records. Do not uninstall to make the
   update pass. A debug build is not a production upgrade.
