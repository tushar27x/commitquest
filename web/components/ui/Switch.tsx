export interface SwitchProps {
  checked: boolean;
  onChange: (checked: boolean) => void;
  /** Id of the visible title (the switch's accessible name). */
  labelledBy: string;
  describedBy?: string;
  disabled?: boolean;
}

/**
 * A pixel on/off switch (docs/design/source/home-SettingsMobile.dc.html): 56×28, gold when on.
 * The tap target grows to 44 px tall around it.
 */
export function Switch({ checked, onChange, labelledBy, describedBy, disabled = false }: SwitchProps) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={checked}
      aria-labelledby={labelledBy}
      aria-describedby={describedBy}
      disabled={disabled}
      onClick={() => onChange(!checked)}
      className={`relative flex h-7 w-14 shrink-0 cursor-pointer p-1 shadow-[0_0_0_2px_var(--color-outline)] after:absolute after:-inset-x-2 after:-inset-y-2 after:content-[''] disabled:cursor-not-allowed disabled:opacity-55 ${
        checked ? "justify-end bg-gold" : "justify-start bg-pedestal"
      }`}
    >
      <span aria-hidden className="block size-5 bg-bone shadow-[0_0_0_2px_var(--color-outline)]" />
    </button>
  );
}
