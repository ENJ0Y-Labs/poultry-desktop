import { useEffect, useMemo, useState } from "react";
import pomasLogo from "../assets/pomas-logo.png";

const STORAGE_KEY = "pomas:onboarding:v1";
const emptyProgress = {
  step: 1, firstName: "", familyName: "", role: "", discovery: "",
  poultryTypes: [], priorities: [], farmCreated: false,
};
const roles = ["HOBBY POULTRY FARMER", "SMALL-SCALE FARM OWNER", "COMMERCIAL FARM OWNER", "FARM MANAGER", "POULTRY FARM WORKER", "POULTRY FARM CONSULTANT", "POULTRY BUSINESS OWNER", "OTHER"];
const discoveryOptions = ["Google / Search Engine", "Facebook", "Instagram", "TikTok", "YouTube", "WhatsApp", "Recommended by a friend or family member", "Farming group or online community", "School or training institution", "AI Assistant (ChatGPT, Gemini, etc.)", "Other"];
const poultryOptions = ["BROILERS", "LAYERS", "TURKEYS", "OTHER POULTRY"];
const priorityOptions = ["FLOCK & BATCH MANAGEMENT", "DAILY FARM RECORDS", "FEED & INVENTORY MANAGEMENT", "BIRD HEALTH & VACCINATION", "EGG PRODUCTION & COLLECTION", "WEIGHT & GROWTH TRACKING", "SALES & CUSTOMER RECORDS", "EXPENSES & FINANCIAL REPORTS", "EVERYTHING IN ONE PLACE", "JUST EXPLORING"];

function readProgress(account) {
  try {
    const saved = JSON.parse(localStorage.getItem(STORAGE_KEY) || "{}");
    const names = String(account?.fullName || "").trim().split(/\s+/);
    return { ...emptyProgress, firstName: names.shift() || "", familyName: names.join(" "), ...saved };
  } catch {
    const names = String(account?.fullName || "").trim().split(/\s+/);
    return { ...emptyProgress, firstName: names.shift() || "", familyName: names.join(" ") };
  }
}

export default function OnboardingWizard({ account, farmForm, setFarmForm, onSaveProfile, onCreateFarm, onComplete, saving, error }) {
  const [progress, setProgress] = useState(() => readProgress(account));
  const [localError, setLocalError] = useState("");
  const step = progress.step;
  const fullName = [progress.firstName, progress.familyName].filter(Boolean).join(" ");
  const initials = useMemo(() => fullName.split(/\s+/).filter(Boolean).map(part => part[0]).join("").slice(0, 2).toUpperCase() || "P", [fullName]);

  useEffect(() => {
    localStorage.setItem(STORAGE_KEY, JSON.stringify({ ...progress, farmDraft: farmForm }));
  }, [progress, farmForm]);

  useEffect(() => {
    if (progress.farmDraft) setFarmForm(current => ({ ...current, ...progress.farmDraft }));
  // Restore the incomplete farm form once when resuming onboarding.
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  function update(key, value) {
    setProgress(current => ({ ...current, [key]: value }));
    setLocalError("");
  }
  function toggle(key, value) {
    setProgress(current => {
      const values = current[key] || [];
      return { ...current, [key]: values.includes(value) ? values.filter(item => item !== value) : [...values, value] };
    });
  }
  async function saveProfileAndContinue() {
    setLocalError("");
    if (!progress.firstName.trim()) return setLocalError("Enter your first name to continue.");
    const saved = await onSaveProfile(fullName);
    if (saved) next();
  }

  function next() {
    if (step === 1 && !progress.firstName.trim()) return setLocalError("Enter your first name to continue.");
    if (step === 2 && !progress.role) return setLocalError("Select the role that best describes you.");
    if (step === 3 && !progress.discovery) return setLocalError("Select how you heard about Pomas.");
    if (step === 4 && !farmCreated) return setLocalError("Create your farm before continuing.");
    if (step === 5 && !progress.poultryTypes.length) return setLocalError("Select at least one poultry type.");
    setLocalError("");
    update("step", Math.min(7, step + 1));
  }
  function back() {
    setLocalError("");
    update("step", Math.max(1, step - 1));
  }
  async function createFarm(event) {
    event.preventDefault();
    setLocalError("");
    if (!farmForm.name.trim()) return setLocalError("Enter your farm name.");
    if (progress.farmCreated) {
      update("step", 5);
      return;
    }
    const created = await onCreateFarm(event);
    if (!created) return;
    const completedFarm = { ...progress, farmCreated: true, step: 5, farmDraft: farmForm };
    localStorage.setItem(STORAGE_KEY, JSON.stringify(completedFarm));
    setProgress(completedFarm);
  }
  function finish() {
    localStorage.setItem("pomas:onboarding:preferences", JSON.stringify({ role: progress.role, discovery: progress.discovery, poultryTypes: progress.poultryTypes, priorities: progress.priorities }));
    localStorage.removeItem(STORAGE_KEY);
    onComplete();
  }

  return (
    <main className="pomas-onboarding">
      <header className="onboarding-topbar">
        <div className="onboarding-brand"><img src={pomasLogo} alt="" /><strong>Pomas</strong></div>
        <span>FARM MANAGEMENT</span>
      </header>
      <div className="onboarding-layout">
        <aside className="onboarding-art" aria-label="Poultry farm illustration">
          <div className="onboarding-grid" aria-hidden="true" />
          <div className="onboarding-art-copy">
            <span>BUILT FOR YOUR FARM</span>
            <h2>Better records.<br/>Healthier flocks.<br/>Stronger decisions.</h2>
            <p>One workspace for the everyday work of poultry farming.</p>
          </div>
          <div className="onboarding-art-scene" aria-hidden="true">
            <div className="onboarding-sun" />
            <div className="onboarding-barn"><span /><i /><b /></div>
            <div className="onboarding-ground" />
            <div className="onboarding-chicken chicken-one">🐔</div>
            <div className="onboarding-chicken chicken-two">🐣</div>
            <div className="onboarding-eggs">🥚 🥚 🥚</div>
            <div className="onboarding-feed">🌾</div>
          </div>
          <small className="onboarding-art-footer">POMAS · YOUR FARM, YOUR RECORDS</small>
        </aside>
        <section className="onboarding-form-panel">
          <div className="onboarding-form-wrap">
            <div className="onboarding-progress">
              <span>STEP {step} OF 7</span>
              <div><i style={{ width: ((step / 7) * 100) + "%" }} /></div>
            </div>
            {step === 1 && <>
              <p className="onboarding-eyebrow">STEP 1 · USER PROFILE</p>
              <h1>Welcome to Pomas!</h1><p className="onboarding-subtitle">Let's get to know you.</p>
              <div className="onboarding-avatar" aria-label="Profile initials">{initials}</div>
              <div className="onboarding-fields">
                <label>First name<input autoFocus value={progress.firstName} onChange={e => update("firstName", e.target.value)} placeholder="First name" /></label>
                <label>Family name<input value={progress.familyName} onChange={e => update("familyName", e.target.value)} placeholder="Family name" /></label>
                <label>Email address<input value={account?.email || ""} readOnly placeholder="Not provided" /><small>Managed through your Pomas account.</small></label>
              </div>
              <button className="onboarding-primary" onClick={saveProfileAndContinue} disabled={saving}>{saving ? "SAVING PROFILE…" : "NEXT"} <span>→</span></button>
            </>}
            {step === 2 && <>
              <p className="onboarding-eyebrow">STEP 2 · YOUR ROLE</p>
              <h1>Quick question, we appreciate it.</h1><p className="onboarding-subtitle">What best describes your role in poultry farming?</p>
              <div className="onboarding-options">{roles.map(role => <label key={role} className={progress.role === role ? "selected" : ""}><input type="radio" name="role" value={role} checked={progress.role === role} onChange={() => update("role", role)} />{role}</label>)}</div>
              <div className="onboarding-actions"><button className="onboarding-secondary" onClick={back}>BACK</button><button className="onboarding-primary" onClick={next}>NEXT <span>→</span></button></div>
            </>}
            {step === 3 && <>
              <p className="onboarding-eyebrow">STEP 3 · DISCOVERY</p>
              <h1>How did you hear about Pomas?</h1><p className="onboarding-subtitle">Select one.</p>
              <div className="onboarding-options">{discoveryOptions.map(option => <label key={option} className={progress.discovery === option ? "selected" : ""}><input type="radio" name="discovery" value={option} checked={progress.discovery === option} onChange={() => update("discovery", option)} />{option}</label>)}</div>
              <div className="onboarding-actions"><button className="onboarding-secondary" onClick={back}>BACK</button><button className="onboarding-primary" onClick={next}>NEXT <span>→</span></button></div>
            </>}
            {step === 4 && <>
              <p className="onboarding-eyebrow">STEP 4 · FARM SETUP</p>
              <h1>Howdy, {progress.firstName || "there"}!</h1><p className="onboarding-subtitle">Let's set up your farm.</p>
              {progress.farmCreated ? (
                <div className="onboarding-fields">
                  <div className="onboarding-create-label">FARM CREATED</div>
                  <h2>{farmForm.name}</h2>
                  <p className="onboarding-privacy">Your farm is already saved. Continue setting up your Pomas workspace.</p>
                  <button className="onboarding-primary" type="button" onClick={next}>CONTINUE <span>→</span></button>
                </div>
              ) : (
                <form className="onboarding-fields" onSubmit={createFarm}>
                  <div className="onboarding-create-label">CREATE NEW FARM</div>
                  <h2>About your farm</h2>
                  <label>Farm name<input required autoFocus value={farmForm.name} onChange={e => setFarmForm(current => ({ ...current, name: e.target.value }))} placeholder="e.g. Grantino Farms" /></label>
                  <label>Farm location<input value={farmForm.location} onChange={e => setFarmForm(current => ({ ...current, location: e.target.value }))} placeholder="Town, city or address" /></label>
                  <div className="onboarding-two-fields">
                    <label>Country<input value="Nigeria" readOnly /></label>
                    <label>Currency<input value="₦ Naira (NGN)" readOnly /></label>
                  </div>
                  <label>Timezone<input value="Africa/Lagos" readOnly /></label>
                  <p className="onboarding-privacy">Your farm records are stored locally on this device. Farm information organizes your records and reports.</p>
                  <button className="onboarding-primary" type="submit" disabled={saving}>{saving ? "CREATING FARM…" : "CREATE FARM & CONTINUE"} <span>→</span></button>
                </form>
              )}
            </>}
            {step === 5 && <>
              <p className="onboarding-eyebrow">STEP 5 · FARM TYPE</p>
              <h1>What type of poultry do you manage?</h1><p className="onboarding-subtitle">Select all that apply.</p>
              <div className="onboarding-options">{poultryOptions.map(option => <label key={option} className={progress.poultryTypes.includes(option) ? "selected" : ""}><input type="checkbox" checked={progress.poultryTypes.includes(option)} onChange={() => toggle("poultryTypes", option)} />{option}</label>)}</div>
              <p className="onboarding-privacy">You can manage multiple poultry types under the same farm.</p>
              <div className="onboarding-actions"><button className="onboarding-secondary" onClick={back}>BACK</button><button className="onboarding-primary" onClick={next}>NEXT <span>→</span></button></div>
            </>}
            {step === 6 && <>
              <p className="onboarding-eyebrow">STEP 6 · YOUR PRIORITIES</p>
              <h1>Last question, promise.</h1><p className="onboarding-subtitle">What would you like Pomas to help you manage most?</p>
              <div className="onboarding-options">{priorityOptions.map(option => <label key={option} className={progress.priorities.includes(option) ? "selected" : ""}><input type="checkbox" checked={progress.priorities.includes(option)} onChange={() => toggle("priorities", option)} />{option}</label>)}</div>
              <div className="onboarding-actions"><button className="onboarding-secondary" onClick={back}>BACK</button><button className="onboarding-primary" onClick={next}>FINISH SETUP <span>→</span></button></div>
            </>}
            {step === 7 && <>
              <p className="onboarding-eyebrow">SETUP COMPLETE</p>
              <div className="onboarding-complete-mark">✓</div>
              <h1>Welcome to Pomas, {progress.firstName || "farmer"}!</h1><p className="onboarding-subtitle">Your farm workspace is ready.</p>
              <h2>Let's make farm management simpler.</h2>
              <ul className="onboarding-next-list"><li>Set up your poultry houses.</li><li>Register your first flock.</li><li>Start recording daily activities.</li><li>Track your farm's performance.</li></ul>
              <div className="onboarding-ready">YOUR FARM IS READY</div>
              <button className="onboarding-primary" onClick={finish}>GO TO DASHBOARD <span>→</span></button>
            </>}
            {(localError || error) && <p className="onboarding-error" role="alert">{localError || error}</p>}
            {step > 1 && step < 7 && step !== 4 && <button className="onboarding-skip" onClick={() => update("step", step + 1)}>SKIP THIS STEP</button>}
          </div>
        </section>
      </div>
    </main>
  );
}
