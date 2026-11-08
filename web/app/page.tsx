import { Landing } from "@/components/landing/Landing";
import "./landing.css";

// The landing page at `/` (docs/design: the "Landing page" boards). PRESS START goes to the
// backend login, or straight to Home with a live session.

export default function LandingPage() {
  return <Landing />;
}
