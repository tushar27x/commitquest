import { EMBERS, starField } from "./data";

// Computed once at module load: the same stars on every render and on the server.
const STARS_A = starField(34, 1920, 560, 7);
const STARS_B = starField(34, 1920, 560, 91);

/** The hero section's scenery, back to front: sky, sun, clouds, hills, castle, mist, foreground. */
export function Backdrop() {
  return (
    <div aria-hidden className="absolute inset-0">
      <div className="l-layer l-sky" />
      <div className="l-sun" />
      <div className="l-cloud l-cloud-high" />
      <div className="l-layer l-far" />
      <div className="l-cloud l-cloud-low" />
      <div className="l-castle" />
      <div className="l-lights" />
      <div className="l-layer l-mid" />
      <div className="l-layer l-mist" />
      <div className="l-layer l-near" />
      <div className="l-twinkle absolute top-0 left-0 size-[2px]" style={{ boxShadow: STARS_A }} />
      <div className="l-twinkle-late absolute top-0 left-0 size-[2px]" style={{ boxShadow: STARS_B }} />
      {EMBERS.map((e) => (
        <div key={e.left} className="l-ember" style={{ left: e.left, animationDelay: e.delay, animationDuration: e.duration }} />
      ))}
    </div>
  );
}
