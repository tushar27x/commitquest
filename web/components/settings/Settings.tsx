"use client";

import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { useRouter } from "next/navigation";
import { useQueryClient } from "@tanstack/react-query";
import { RoadBlockedCard } from "@/components/game/StateCards";
import { Switch } from "@/components/ui/Switch";
import { WEEKDAYS, type Preferences, type Weekday } from "@/lib/api/types";
import { clearToken } from "@/lib/auth";
import { formatCountdown } from "@/lib/format";
import {
  MAX_REST_DAYS,
  WEEKDAY_LETTERS,
  browserTimeZone,
  fieldErrors,
  restDayLocked,
  samePreferences,
  sameTimeZone,
  timeZoneOptions,
  toggleRestDay,
  weekdayName,
  type FieldErrors,
} from "@/lib/preferences";
import { usePreferences, useSavePreferences } from "@/lib/queries";

/** Taps within this window go out as one `PUT` (swapping a rest day is two taps). */
const SAVE_DELAY_MS = 500;

const SECTION = "flex flex-col bg-stone p-3.5 shadow-[0_0_0_3px_var(--color-outline)] md:p-5";
const HEADING = "font-display text-[9px] leading-normal md:text-[10px]";

/** Settings (docs/design/png/settings-phone.png). Rendered inside <Screen>. */
export function Settings() {
  const preferences = usePreferences();
  if (preferences.data) return <SettingsForm saved={preferences.data} />;
  if (preferences.error) {
    // 401 is the shell's "Session ended" dialog.
    if (preferences.error.status === 401) return null;
    return <RoadBlockedCard onRetry={() => void preferences.refetch()} retrying={preferences.isFetching} />;
  }
  return (
    <p role="status" className="font-display text-[10px] text-muted-2">
      LOADING…
    </p>
  );
}

type SaveStatus =
  | { kind: "idle" }
  | { kind: "saving" }
  | { kind: "saved"; restDaysChanged: boolean }
  | { kind: "invalid"; errors: FieldErrors }
  | { kind: "resting"; until: number }
  | { kind: "blocked" };

function SettingsForm({ saved }: { saved: Preferences }) {
  const router = useRouter();
  const queryClient = useQueryClient();
  const { mutate } = useSavePreferences();

  // The user's unsaved edits; null when the screen shows what the server has.
  const [draft, setDraft] = useState<Preferences | null>(null);
  const [status, setStatus] = useState<SaveStatus>({ kind: "idle" });
  const value = draft ?? saved;
  const timer = useRef<number | undefined>(undefined);
  const queued = useRef<Preferences | null>(null);

  const send = useCallback(
    (next: Preferences, previous: Preferences) => {
      setStatus({ kind: "saving" });
      mutate(next, {
        onSuccess: (result) => {
          // Drop the draft once the server has it (unless the user changed something since).
          setDraft((d) => (d && samePreferences(d, next) ? null : d));
          setStatus({ kind: "saved", restDaysChanged: !samePreferences({ ...previous, rest_days: result.rest_days }, previous) });
        },
        onError: (error) => {
          const invalid = fieldErrors(error);
          if (invalid) setStatus({ kind: "invalid", errors: invalid });
          else if (error.status === 401) setStatus({ kind: "idle" }); // "Session ended" takes over.
          else if (error.status === 429) setStatus({ kind: "resting", until: Date.now() + (error.retryAfter ?? 60) * 1000 });
          else setStatus({ kind: "blocked" });
        },
      });
    },
    [mutate],
  );

  const flush = useCallback(() => {
    window.clearTimeout(timer.current);
    const next = queued.current;
    queued.current = null;
    if (next) send(next, saved);
  }, [send, saved]);

  // Leaving the screen still saves the last change.
  const flushRef = useRef(flush);
  useEffect(() => {
    flushRef.current = flush;
  }, [flush]);
  useEffect(() => () => flushRef.current(), []);

  const change = (patch: Partial<Preferences>) => {
    const next = { ...value, ...patch };
    setDraft(next);
    if (status.kind === "resting" && status.until > Date.now()) return; // Saved by RETRY after the wait.
    queued.current = next;
    window.clearTimeout(timer.current);
    timer.current = window.setTimeout(flush, SAVE_DELAY_MS);
  };

  const retry = () => {
    queued.current = value;
    flush();
  };

  const logOut = () => {
    clearToken();
    queryClient.clear();
    router.replace("/");
  };

  const errors = status.kind === "invalid" ? status.errors.byField : {};

  return (
    <div className="flex w-full flex-col gap-4 md:max-w-[640px] md:gap-6">
      <RestDays days={value.rest_days} error={errors.rest_days} onChange={(rest_days) => change({ rest_days })} />
      <TimeZone zone={value.timezone} error={errors.timezone} onChange={(timezone) => change({ timezone })} />

      <section aria-label="Notifications and privacy" className={`${SECTION} px-3.5 py-1.5 md:px-5 md:py-2`}>
        <ToggleRow
          id="alerts"
          title="EMAIL ALERTS"
          help="Low on hearts, level changes"
          checked={value.alerts_enabled}
          error={errors.alerts_enabled}
          onChange={(alerts_enabled) => change({ alerts_enabled })}
          divider
        />
        <ToggleRow
          id="arena"
          title="SHOW ME IN THE ARENA"
          help="Public leaderboard, by class"
          checked={value.public_profile}
          error={errors.public_profile}
          onChange={(public_profile) => change({ public_profile })}
        />
      </section>

      <SaveStatusLine status={status} onRetry={retry} />

      <p className="px-1 text-[18px] leading-[1.05] text-muted-2">
        Insight Hub reads public data. Private work counts only if your GitHub profile shows private contributions.
      </p>

      <button
        type="button"
        onClick={logOut}
        className="mt-4 h-12 bg-transparent font-display text-[10px] text-ember shadow-[0_0_0_3px_var(--color-ember)] hover:bg-ember-fill/40 active:bg-ember-fill/40 md:w-60"
      >
        LOG OUT
      </button>
    </div>
  );
}

function FieldMessage({ id, children }: { id: string; children?: string }) {
  if (!children) return null;
  return (
    <p id={id} className="text-ember">
      {children}
    </p>
  );
}

function RestDays({ days, error, onChange }: { days: Weekday[]; error?: string; onChange: (days: Weekday[]) => void }) {
  return (
    <section aria-labelledby="rest-title" className={`${SECTION} gap-3`}>
      <div className="flex items-baseline justify-between gap-3">
        <h2 id="rest-title" className={HEADING}>
          REST DAYS
        </h2>
        <span className="text-[18px] text-muted-2" aria-live="polite">
          {days.length} of {MAX_REST_DAYS} picked
        </span>
      </div>
      <div
        role="group"
        aria-label={`Rest days, pick up to ${MAX_REST_DAYS}`}
        aria-describedby={error ? "rest-error rest-help" : "rest-help"}
        className="grid grid-cols-7 gap-1.5"
      >
        {WEEKDAYS.map((day) => {
          const on = days.includes(day);
          const locked = restDayLocked(days, day);
          return (
            <button
              key={day}
              type="button"
              aria-pressed={on}
              aria-label={weekdayName(day)}
              disabled={locked}
              onClick={() => onChange(toggleRestDay(days, day))}
              className={`h-11 min-w-0 font-display text-[9px] disabled:cursor-not-allowed disabled:opacity-45 md:h-12 md:text-[10px] ${
                on
                  ? "bg-rest text-gold shadow-[0_0_0_2px_var(--color-outline),inset_0_0_0_3px_var(--color-gold)]"
                  : "bg-stone-lit text-muted shadow-[0_0_0_2px_var(--color-outline)] hover:text-gold active:text-gold disabled:hover:text-muted"
              }`}
            >
              {WEEKDAY_LETTERS[day]}
            </button>
          );
        })}
      </div>
      <FieldMessage id="rest-error">{error}</FieldMessage>
      <p id="rest-help" className="text-[19px] leading-[1.05] text-muted">
        Missing a rest day costs no heart and doesn&apos;t break your streak. Changes apply from tomorrow.
      </p>
    </section>
  );
}

function TimeZone({ zone, error, onChange }: { zone: string; error?: string; onChange: (zone: string) => void }) {
  const detected = useMemo(() => browserTimeZone(), []);
  const options = useMemo(() => timeZoneOptions(zone, detected), [zone, detected]);
  const isDetected = sameTimeZone(detected, zone);
  return (
    <section className={`${SECTION} gap-2`}>
      <label htmlFor="timezone" className={HEADING}>
        YOUR DAY ENDS AT MIDNIGHT IN
      </label>
      <div className="relative">
        <select
          id="timezone"
          value={zone}
          onChange={(e) => onChange(e.target.value)}
          aria-invalid={error ? true : undefined}
          aria-describedby={error ? "timezone-error" : undefined}
          className="h-11 w-full min-w-0 cursor-pointer appearance-none bg-stone-lit py-0 pr-28 pl-3 text-[21px] text-bone shadow-[0_0_0_2px_var(--color-outline)] md:h-12"
        >
          {options.map((z) => (
            <option key={z} value={z}>
              {z}
            </option>
          ))}
        </select>
        <span
          aria-hidden
          className={`pointer-events-none absolute top-1/2 right-3 -translate-y-1/2 font-display text-[7px] ${isDetected ? "text-success" : "text-muted-2"}`}
        >
          {isDetected ? "DETECTED" : "CHANGE"} ▼
        </span>
      </div>
      <FieldMessage id="timezone-error">{error}</FieldMessage>
      {detected && !isDetected && (
        <button
          type="button"
          onClick={() => onChange(detected)}
          className="relative self-start py-2 text-left text-gold after:absolute after:inset-x-0 after:top-1/2 after:h-11 after:-translate-y-1/2 after:content-[''] hover:underline"
        >
          Use this device&apos;s zone: {detected}
        </button>
      )}
    </section>
  );
}

function ToggleRow({
  id,
  title,
  help,
  checked,
  error,
  onChange,
  divider = false,
}: {
  id: string;
  title: string;
  help: string;
  checked: boolean;
  error?: string;
  onChange: (checked: boolean) => void;
  divider?: boolean;
}) {
  return (
    <div className={`flex flex-col gap-1 py-2.5 ${divider ? "shadow-[0_2px_0_var(--color-stone-lit)]" : ""}`}>
      <div className="flex items-center justify-between gap-3">
        <div className="flex min-w-0 flex-col gap-1">
          <span id={`${id}-title`} className="font-display text-[8px] leading-normal md:text-[9px]">
            {title}
          </span>
          <span id={`${id}-help`} className="text-[18px] leading-none text-muted-2">
            {help}
          </span>
        </div>
        <Switch checked={checked} onChange={onChange} labelledBy={`${id}-title`} describedBy={`${id}-help`} />
      </div>
      <FieldMessage id={`${id}-error`}>{error}</FieldMessage>
    </div>
  );
}

function SaveStatusLine({ status, onRetry }: { status: SaveStatus; onRetry: () => void }) {
  const resting = status.kind === "resting";
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    if (!resting) return;
    const t = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(t);
  }, [resting]);

  if (status.kind === "blocked") return <RoadBlockedCard onRetry={onRetry} />;

  let content: ReactNode = null;
  switch (status.kind) {
    case "saving":
      content = <span className="text-muted-2">SAVING…</span>;
      break;
    case "saved":
      content = (
        <span className="text-success">
          SAVED{status.restDaysChanged ? " · NEW REST DAYS START TOMORROW" : ""}
        </span>
      );
      break;
    case "invalid":
      content = (
        <span className="text-ember">
          NOT SAVED{status.errors.other.length > 0 ? `: ${status.errors.other.join(" ")}` : ": fix the field marked above."}
        </span>
      );
      break;
    case "resting": {
      const left = Math.max(0, Math.ceil((status.until - now) / 1000));
      content = (
        <span className="flex flex-wrap items-center gap-x-4 gap-y-2 text-ember">
          {left > 0 ? `TOO MANY CHANGES · TRY AGAIN IN ${formatCountdown(left)}` : "NOT SAVED YET"}
          <button
            type="button"
            onClick={onRetry}
            disabled={left > 0}
            className="relative font-display text-[9px] text-gold after:absolute after:inset-x-0 after:top-1/2 after:h-11 after:-translate-y-1/2 after:content-[''] disabled:text-faint"
          >
            RETRY
          </button>
        </span>
      );
      break;
    }
  }
  return (
    <p role="status" className="min-h-6 px-1 font-display text-[8px] leading-normal">
      {content}
    </p>
  );
}
