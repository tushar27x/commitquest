import type { Metadata, Viewport } from "next";
import { Press_Start_2P, VT323 } from "next/font/google";
import { Providers } from "./providers";
import "./globals.css";

// Display: headings, HUD, buttons, short labels (10–24 px).
const pressStart = Press_Start_2P({
  weight: "400",
  subsets: ["latin"],
  variable: "--font-press-start-2p",
});

// Body and AI text, never below 20 px.
const vt323 = VT323({
  weight: "400",
  subsets: ["latin"],
  variable: "--font-vt323",
});

export const metadata: Metadata = {
  title: "CommitQuest",
  description: "Keep your hero alive with your commits.",
  // Browser tabs and iOS "Add to Home Screen" (the manifest covers Android and desktop Chrome);
  // app/favicon.ico is added by Next itself.
  icons: {
    icon: [
      { url: "/icons/favicon-16x16.png", sizes: "16x16", type: "image/png" },
      { url: "/icons/favicon-32x32.png", sizes: "32x32", type: "image/png" },
      { url: "/icons/favicon-48x48.png", sizes: "48x48", type: "image/png" },
    ],
    apple: [{ url: "/icons/apple-touch-icon.png", sizes: "180x180", type: "image/png" }],
  },
  appleWebApp: { capable: true, title: "CommitQuest", statusBarStyle: "black" },
};

export const viewport: Viewport = {
  themeColor: "#13111C",
  viewportFit: "cover",
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html lang="en" className={`${pressStart.variable} ${vt323.variable}`}>
      <body>
        <Providers>{children}</Providers>
      </body>
    </html>
  );
}
