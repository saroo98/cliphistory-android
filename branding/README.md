# ClipHistory artwork

Approved artwork from `ClipHistory-logo-final.zip`, integrated for release 1.3.4. `asset-manifest.json` records the archive digest and exact copied source/destination hashes.

| Surface | Asset | Size / use |
| --- | --- | --- |
| Android launcher, splash, Settings and Recents | `app/src/main/res/mipmap-anydpi/ic_launcher.xml` | 108dp adaptive foreground, fixed green background; the supplied solid foreground also serves as the identical monochrome layer. The 56dp mark fits the 66dp safe region. Android applies the mask. |
| Quick Settings tile, tile confirmation and recovery notification | `app/src/main/res/drawable/ic_cliphistory_system.xml` | Supplied 24dp monochrome vector, tinted by Android. |
| Welcome and Settings > About | Same adaptive launcher resource | 48dp; adjacent accessible app name. No extra font or raster asset in the APK. |
| F-Droid catalog | `fastlane/metadata/android/en-US/images/icon.png` | Supplied 512 × 512 PNG. |
| F-Droid feature graphic | `fastlane/metadata/android/en-US/images/featureGraphic.png` | Supplied 1024 × 500 PNG. |
| GitHub README | `cliphistory.svg`, `cliphistory-dark.svg` | Transparent outlined wordmarks; light/dark selected with a picture element. |
| GitHub repository social preview | `social-preview.png` | Supplied 1280 x 640 PNG, prepared for repository Settings. Upload remains pending browser permission; see the publication report. |
| Local HTML reference | Embedded favicon, wordmark and catalog icon | Self-contained; no external asset requests. |
| Future website favicon | `favicon.svg`, `favicon.ico` | Supplied SVG and 16/32/48px ICO. No public website exists for this project. |

The detailed wordmark and compact Android mark intentionally differ in fine detail. Use the provided exports without converting the feature graphic into a launcher icon. Functional Copy, Search and other action icons retain their meaning.

Artwork provenance is in `ARTWORK-PROVENANCE.md`. Inter lettering is outlined; the font binary is not included. Its source licence is retained in `LICENSES/Inter-OFL.txt`. Proof sheets and the logo designer's internal scores are not runtime evidence or F-Droid approval.
