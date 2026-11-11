import type { MetadataRoute } from "next";

// The web app manifest: what "Install app" (Android, desktop Chrome) and "Add to Home Screen" use.
// `start_url` is /home: with a live session it opens the hero straight away, without one Home sends the
// user to the landing page. Colours match `viewport.themeColor` in the root layout.
export default function manifest(): MetadataRoute.Manifest {
  return {
    name: "CommitQuest",
    short_name: "CommitQuest",
    description: "Keep your hero alive with your commits.",
    start_url: "/home",
    scope: "/",
    display: "standalone",
    orientation: "portrait",
    background_color: "#13111C",
    theme_color: "#13111C",
    icons: [
      { src: "/icons/icon-192.png", sizes: "192x192", type: "image/png", purpose: "any" },
      { src: "/icons/icon-512.png", sizes: "512x512", type: "image/png", purpose: "any" },
      // Padded so Android's circle / squircle crop keeps the heroes and hearts inside the safe zone.
      { src: "/icons/icon-maskable-512.png", sizes: "512x512", type: "image/png", purpose: "maskable" },
    ],
  };
}
