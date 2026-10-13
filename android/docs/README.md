# Arcana Table project page

This folder contains the static GitHub Pages site. The workflow in
`.github/workflows/deploy-pages.yml` publishes the page and the Build 66 test
APK whenever the site changes on `main`.

## Enable publishing

1. Push this project to a GitHub repository on the `main` branch.
2. In the repository, open **Settings → Pages** and choose **GitHub Actions**
   as the build and deployment source.
3. Push to `main`, or run **Deploy Arcana Table project page** from the
   repository's **Actions** tab.

The page will appear at `https://<owner>.github.io/<repository>/` after the
workflow succeeds. The APK is copied to the site's `downloads/` folder during
the workflow; the large binary does not need to be copied into `docs/`.

The site describes Build 66. Update its version and APK filename in
`index.html` and the Pages workflow when publishing a later build.
