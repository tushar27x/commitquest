"use client";

import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { useRouter } from "next/navigation";
import { useQueryClient } from "@tanstack/react-query";
import { RoadBlockedCard } from "@/components/game/StateCards";
import { Switch } from "@/components/ui/Switch";
import { WEEKDAYS, type Preferences, type Weekday } from "@/lib/api/types";
import { clearToken, loginUrl, privateLoginUrl } from "@/lib/auth";
import { clockNow, useNow } from "@/lib/clock";
import { formatCountdown } from "@/lib/format";
import {
  CLOSED,
  PRIVATE_RETURN_PARAM,
  isEffectiveChange,
  isPrivateLockedError,
  isWaitingForGithub,
  lockDateLabel,
  lockedUntilMs,
  markPrivateReauth,
  privateSwitchLocked,
  returnOutcome,
  warningStep,
  type ReturnOutcome,
  type WarningAction,
  type WarningState,
} from "@/lib/private";
import { PrivateWarning } from "./PrivateWarning";
import {
  MAX_REST_DAYS,
  WEEKDAY_LETTERS,
  browserTimeZone,
  fieldErrors,
  privateState,
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

/** The private switch has its own status: it is sent at once (no debounce) and a flip can take ~90 s. */
type PrivateStatus =
  | { kind: "idle" }
  | { kind: "saving" }
  | { kind: "resyncing"; to: boolean }
  | { kind: "done"; to: boolean; resynced: boolean }
  | { kind: "locked"; until: number }
  | { kind: "reauth" }
  | { kind: "unavailable" }
  | { kind: "resting"; seconds: number }
  | { kind: "invalid"; message: string };

/** `?private=back`: the token hand-off sends us here after a private re-auth. */
function cameBackFromGithub(): boolean {
  return new URLSearchParams(window.location.search).get(PRIVATE_RETURN_PARAM) === "back";
}

function SettingsForm({ saved }: { saved: Preferences }) {
  const router = useRouter();
  const queryClient = useQueryClient();
  const { mutate } = useSavePreferences();
  const privateSave = useSavePreferences();
  const now = useNow(30_000);
  const [privateStatus, setPrivateStatus] = useState<PrivateStatus>({ kind: "idle" });
  const [warning, setWarning] = useState<WarningState>(CLOSED);
  // What GitHub said, read once from the preferences we just loaded (the outcome of a private re-auth).
  const [returned, setReturned] = useState<ReturnOutcome>(() =>
    cameBackFromGithub() ? returnOutcome(saved, clockNow()) : { kind: "none" },
  );
  useEffect(() => {
    if (!cameBackFromGithub()) return;
    const url = new URL(window.location.href);
    url.searchParams.delete(PRIVATE_RETURN_PARAM);
    window.history.replaceState(window.history.state, "", url);
  }, []);

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

  // The private switch is sent on its own (below); every other save carries the server's value for it.
  const change = (patch: Partial<Preferences>) => {
    const next = { ...value, ...patch, include_private: saved.include_private };
    setDraft(next);
    if (status.kind === "resting" && status.until > Date.now()) return; // Saved by RETRY after the wait.
    queued.current = next;
    window.clearTimeout(timer.current);
    timer.current = window.setTimeout(flush, SAVE_DELAY_MS);
  };

  const retry = () => {
    queued.current = { ...value, include_private: saved.include_private };
    flush();
  };

  const resyncing = privateStatus.kind === "resyncing";
  // A flip resyncs inside the request (up to ~90 s): keep the tab open.
  useEffect(() => {
    if (!resyncing) return;
    const guard = (e: BeforeUnloadEvent) => e.preventDefault();
    window.addEventListener("beforeunload", guard);
    return () => window.removeEventListener("beforeunload", guard);
  }, [resyncing]);

  /** Sends the switch's new value at once: it carries the other edits, and nothing is shown before the server says so. */
  const submitPrivate = (include: boolean) => {
    if (privateSave.isPending) return;
    window.clearTimeout(timer.current);
    queued.current = null;
    const next: Preferences = { ...value, include_private: include };
    const effective = isEffectiveChange(saved, include);
    setReturned({ kind: "none" });
    setPrivateStatus(effective ? { kind: "resyncing", to: include } : { kind: "saving" });
    privateSave.mutate(next, {
      onSuccess: () => {
        setDraft((d) => (d && samePreferences({ ...d, include_private: include }, next) ? null : d));
        setPrivateStatus({ kind: "done", to: include, resynced: effective });
      },
      onError: (error) => {
        // Nothing changed on the server and nothing was shown early, so the switch is already right.
        if (isPrivateLockedError(error)) {
          setPrivateStatus({ kind: "locked", until: clockNow() + (error.retryAfter ?? 0) * 1000 });
        } else if (error.status === 401) setPrivateStatus({ kind: "idle" }); // "Session ended" takes over.
        else if (error.status === 409) setPrivateStatus({ kind: "reauth" });
        else if (error.status === 429) setPrivateStatus({ kind: "resting", seconds: error.retryAfter ?? 60 });
        else if (fieldErrors(error)) {
          const invalid = fieldErrors(error);
          setPrivateStatus({ kind: "invalid", message: invalid?.byField.include_private ?? invalid?.other.join(" ") ?? "The server refused these settings." });
        } else setPrivateStatus({ kind: "unavailable" });
      },
    });
  };

  /** The switch asks; only CONFIRM in the warning sends (waiting-to-off changes nothing effective, so it needs no warning). */
  const askPrivate = (checked: boolean) => {
    if (!checked && isWaitingForGithub(saved)) submitPrivate(false);
    else warn({ type: "open", kind: checked ? "on" : "off" });
  };

  const warn = (action: WarningAction) => {
    const step = warningStep(warning, action);
    setWarning(step.state);
    if (step.send !== null) submitPrivate(step.send);
  };

  const logOut = () => {
    clearToken();
    queryClient.clear();
    router.replace("/");
  };

  const errors = status.kind === "invalid" ? status.errors.byField : {};

  return (
    <div className="flex w-full flex-col gap-4 md:gap-6">
      {/* While the resync runs nothing else may be saved: a second PUT would carry a stale switch. */}
      <fieldset disabled={resyncing} className="m-0 flex min-w-0 flex-col gap-4 border-0 p-0 md:gap-6">
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
          divider
        />
        <PrivateRepos
          prefs={saved}
          now={now}
          status={privateStatus}
          returned={returned}
          error={errors.include_private}
          onChange={askPrivate}
        />
      </section>
      </fieldset>
      <PrivateWarning
        state={warning}
        access={saved.private_access}
        onTick={(understood) => warn({ type: "tick", understood })}
        onCancel={() => warn({ type: "cancel" })}
        onConfirm={() => warn({ type: "confirm" })}
      />

      {/* The save status (a live region) takes no room while it's empty, so the gaps stay even. */}
      <div className="flex flex-col">
        <SaveStatusLine status={status} onRetry={retry} />
        <p className="text-center text-[18px] leading-[1.05] text-balance text-muted-2">
          Public repos always count. Private repos count only while SHARE PRIVATE REPOS is on. Your GitHub calendar may still include private counts if your profile shows them.
        </p>
      </div>

      <button
        type="button"
        onClick={logOut}
        className="h-12 w-full bg-transparent font-display text-[10px] text-ember shadow-[0_0_0_3px_var(--color-ember)] hover:bg-ember-fill/40 active:bg-ember-fill/40"
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

const POINT = "relative pl-4 before:absolute before:top-[0.45em] before:left-0 before:size-1.5 before:bg-muted-2 before:content-['']";

/** B10/B11: "share private repos" (contract, include_private x private_access x lock). */
function PrivateRepos({
  prefs,
  now,
  status,
  returned,
  error,
  onChange,
}: {
  prefs: Preferences;
  now: number;
  status: PrivateStatus;
  returned: ReturnOutcome;
  error?: string;
  onChange: (checked: boolean) => void;
}) {
  const state = privateState(prefs);
  const lockedMs = lockedUntilMs(prefs, now);
  const lockDate = prefs.private_locked_until && lockedMs !== null ? lockDateLabel(prefs.private_locked_until) : null;
  const switchLocked = privateSwitchLocked(prefs, now);
  const busy = status.kind === "resyncing" || status.kind === "saving";
  const waiting = state === "needs-permission";

  let help = "Count my private repos too";
  let helpTone = "text-muted-2";
  if (status.kind === "resyncing") {
    help = "RESYNCING YOUR INSIGHTS…";
    helpTone = "text-gold";
  } else if (lockDate && !waiting) {
    help = `LOCKED UNTIL ${lockDate.toUpperCase()}`;
    helpTone = "text-gold";
  } else if (state === "active") {
    help = "ON · ACTIVE";
    helpTone = "text-success";
  } else if (waiting) {
    help = "ON · NEEDS GITHUB PERMISSION";
    helpTone = "text-gold";
  }

  return (
    <div className="flex flex-col gap-2 py-2.5">
      <div className="flex items-center justify-between gap-3">
        <div className="flex min-w-0 flex-col gap-1">
          <span id="private-title" className="font-display text-[8px] leading-normal md:text-[9px]">
            SHARE PRIVATE REPOS
          </span>
          <span id="private-help" className={`text-[18px] leading-none ${helpTone}`}>
            {help}
          </span>
        </div>
        <Switch
          checked={prefs.include_private}
          onChange={onChange}
          labelledBy="private-title"
          describedBy="private-help private-note private-lock"
          disabled={switchLocked || busy}
        />
      </div>

      {status.kind === "resyncing" && (
        <div role="status" className="flex flex-col gap-1.5 bg-stone-lit p-2.5">
          <span className="resync-bar h-2 w-full bg-pedestal" aria-hidden />
          <span className="text-[19px] leading-[1.05] text-gold">
            {status.to ? "Reading your private repos and recounting." : "Removing your private work and recounting."} This can take up to 90 seconds. Don&apos;t close this tab.
          </span>
        </div>
      )}

      {lockDate && (
        <p id="private-lock" className="text-[18px] leading-[1.05] text-gold">
          {waiting
            ? `Locked until ${lockDate}: GitHub access can't be turned on before then. You can still turn this off.`
            : `After you change this, it is locked for 7 days (until ${lockDate}).`}
        </p>
      )}

      <p id="private-note" className="text-[18px] leading-[1.05] text-muted-2">
        Count my private repos in my own stats. Off by default. We read; we never write, and we never read your code.
      </p>
      <details className="group text-[18px] leading-[1.05] text-muted-2">
        <summary className="relative flex min-h-11 cursor-pointer items-center font-display text-[8px] text-gold select-none">WHAT WE READ</summary>
        <ul className="flex flex-col gap-2 pb-1">
          <li className={POINT}>We read commit messages (the latest few), README text, the names of files and folders in the repo root, and activity counts.</li>
          <li className={POINT}>Never your source code or diffs.</li>
          <li className={POINT}>
            GitHub only offers one all-or-nothing permission for private repos, repo, which is read AND write. We only read.
          </li>
          <li className={POINT}>Private repo names, READMEs and messages are never shown publicly or on the Arena.</li>
          <li className={POINT}>You can change this once a week: after a change it is locked for 7 days.</li>
          <li className={POINT}>
            If your GitHub profile has Include private contributions on, anonymous private counts can stay in your calendar and streaks even when this is off. Turn that
            off in your GitHub profile settings.
          </li>
          <li className={POINT}>To remove our access completely, revoke the app at github.com/settings/applications.</li>
        </ul>
      </details>

      {waiting && status.kind !== "resyncing" && (
        <div className="flex flex-col items-start gap-1">
          {status.kind === "saving" ? (
            <span className="font-display text-[8px] text-muted-2">SAVING…</span>
          ) : lockDate ? (
            <button
              type="button"
              disabled
              className="flex h-11 items-center bg-stone-lit px-4 font-display text-[9px] text-faint shadow-[0_0_0_2px_var(--color-outline)] disabled:cursor-not-allowed"
            >
              AUTHORIZE ON GITHUB
            </button>
          ) : (
            <a
              href={privateLoginUrl()}
              onClick={markPrivateReauth}
              className="flex h-11 items-center bg-gold px-4 font-display text-[9px] text-outline shadow-[0_0_0_2px_var(--color-outline)] hover:bg-bone active:bg-bone"
            >
              AUTHORIZE ON GITHUB
            </a>
          )}
          <span className="text-[18px] leading-[1.05] text-muted-2">Nothing private is read until GitHub says yes.</span>
        </div>
      )}

      <PrivateOutcome status={status} returned={returned} />
      <FieldMessage id="private-error">{error}</FieldMessage>
    </div>
  );
}

/** The result of the last try: success, the lock, or why nothing changed. A live region, empty when idle. */
function PrivateOutcome({ status, returned }: { status: PrivateStatus; returned: ReturnOutcome }) {
  let content: ReactNode = null;
  let tone = "text-muted-2";
  switch (status.kind) {
    case "done":
      tone = "text-success";
      content = status.resynced ? (status.to ? "PRIVATE REPOS ARE ON · INSIGHTS RESYNCED" : "PRIVATE REPOS ARE OFF · INSIGHTS RESYNCED") : "SAVED";
      break;
    case "locked":
      tone = "text-ember";
      content = `NOT CHANGED · LOCKED UNTIL ${lockDateLabel(new Date(status.until).toISOString()).toUpperCase()}`;
      break;
    case "reauth":
      tone = "text-ember";
      content = (
        <>
          NOT CHANGED · GITHUB NEEDS YOU TO LOG IN AGAIN{" "}
          <a href={loginUrl()} className="relative text-gold underline after:absolute after:inset-x-0 after:top-1/2 after:h-11 after:-translate-y-1/2 after:content-['']">
            RECONNECT GITHUB
          </a>
        </>
      );
      break;
    case "unavailable":
      tone = "text-ember";
      content = "NOT CHANGED · GITHUB IS UNAVAILABLE. TRY AGAIN IN A MOMENT";
      break;
    case "resting":
      tone = "text-ember";
      content = `NOT CHANGED · TOO MANY TRIES. TRY AGAIN IN ${formatCountdown(status.seconds)}`;
      break;
    case "invalid":
      tone = "text-ember";
      content = `NOT CHANGED · ${status.message}`;
      break;
    default:
      if (returned.kind === "on") {
        tone = "text-success";
        content = "GITHUB SAID YES · PRIVATE REPOS ARE ON";
      } else if (returned.kind === "locked-recorded") {
        tone = "text-gold";
        content = `LOCKED UNTIL ${lockDateLabel(returned.until).toUpperCase()} · THE PERMISSION WAS RECORDED, BUT ACCESS STAYS OFF`;
      } else if (returned.kind === "declined") {
        tone = "text-gold";
        content = "GITHUB DIDN'T SHARE YOUR PRIVATE REPOS · AUTHORIZE AGAIN WHEN YOU'RE READY";
      }
  }
  return (
    <p role="status" className={`font-display text-[8px] leading-normal empty:hidden ${tone}`}>
      {content}
    </p>
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
    <p role="status" className="flex min-h-6 items-center justify-center pb-2 text-center font-display text-[8px] leading-normal empty:min-h-0 empty:pb-0">
      {content}
    </p>
  );
}
