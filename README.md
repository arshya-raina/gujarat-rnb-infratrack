# Gujarat R&B InfraTrack

**Statewide Infrastructure Asset & Lifecycle Management Platform for the Roads & Buildings Department, Government of Gujarat**

InfraTrack gives the R&B Department one digital register for every road, bridge, culvert, building and government facility under its jurisdiction. Each asset is followed through its whole life: sanction, construction, inspection, maintenance and repair. Every officer sees exactly the part of Gujarat they are responsible for, from the Secretary's statewide view down to a single sub-division.

> All data in this prototype is fictional demo data generated for the hackathon.

**Backend:** Java 17 with an embedded H2 SQL database. **Frontend:** React (already built and included).

---

## Run it (the only thing you need is Java)

1. Install **Java 17 or newer**. The easiest option is Temurin from https://adoptium.net (choose the JDK, keep the default installer options).
2. Open this folder and:
   - **Windows:** double-click `run.bat`
   - **Mac / Linux:** run `./run.sh` in a terminal
3. When the window says *"Gujarat R&B InfraTrack is running"*, open **http://localhost:8080**.

The first start takes about 10 seconds while it generates the Gujarat dataset. After that it starts in about 2 seconds, and your changes (inspections, bills, complaints) are saved in the `data/` folder.

To reset the demo data to its original state: `run.bat --reseed` (or `./run.sh --reseed`), or simply delete the `data` folder.

Keep the window open while you demo; closing it stops the server.

### Other ways to run

- **From IntelliJ IDEA / Eclipse / VS Code:** open the `backend` folder (it imports as a Maven project via `pom.xml`) and run `gov.gujarat.rnb.infratrack.App`. Set the working directory to the project root so it finds `frontend/dist`.
- **Docker:** `docker compose up --build`, then open http://localhost:8080.
- **Different port:** set the `PORT` environment variable, e.g. `set PORT=9090` before `run.bat`.

---

## Demo accounts

Every account uses the password **`demo123`**. The login page also has one-click buttons for each role.

| Role | Email | Sees |
|---|---|---|
| State Administrator | admin@rnb.gujarat.gov.in | All of Gujarat |
| Chief / Regional Officer | ce.surat@rnb.gujarat.gov.in | Surat Circle |
| Division Engineer | ee.suratcity@rnb.gujarat.gov.in | Surat City Division |
| Sub-Division Engineer | dyee.athwa@rnb.gujarat.gov.in | Athwa Sub-Division |
| Contractor | contractor@sabarmatiinfra.in | Works awarded to Sabarmati Infracon |
| Citizen | citizen@example.com | Public portal and own complaints |

Jurisdiction limits are enforced on the server for every request, not just hidden in the UI. For example, the Surat officer gets `403 Forbidden` on any Ahmedabad record.

---

## 3-minute demo script for judges

1. **Command center (State Administrator).** Open with the headline: 18,492 assets, 1,283 active projects, 427 maintenance due, 61 critical. The circle board reads like a highway sign: Ahmedabad 4,281, Gandhinagar 2,104, Surat 3,762, Vadodara 3,019, Rajkot 2,871, Others 2,455. The map shows every critical asset in the state.
2. **Drill down.** Click **Surat**, then **Surat City Division**, then **Athwa**. Each level shows its own stats, map and active works, ending at individual assets plotted by condition.
3. **One asset.** Open a critical bridge. Show the health forecast (recorded inspections, projected decline, months to critical, recommended action and cost). Log a new inspection and watch the condition, risk and due date update instantly.
4. **Maintenance priority.** Show the ranked list: the platform tells the department where this year's budget should go first.
5. **Contractor.** Sign out, sign in as the contractor. Report progress on a work and submit a running account bill.
6. **Approval.** Sign in as the Division Engineer, open the same work, then verify, approve and pay the bill. Expenditure updates.
7. **Citizen.** Open the public portal, drop a pin in Surat, report a cracked bridge. It is auto-routed to the nearest sub-division with a ticket number and appears on the Athwa engineer's complaints desk.
8. **Scale story.** The prototype models 8 circles, 17 divisions and 50 sub-divisions; the same hierarchy extends to all 33 districts without code changes.

---

## What it does

- **Unified infrastructure registry:** roads (State Highways, MDR, ODR, village roads), major and minor bridges, culverts, buildings (PHCs, schools, Taluka Seva Sadans, courts) and government facilities, each with an asset code, location, age, traffic, replacement value and condition index.
- **Hierarchy drill-down:** State, Circle, Division, Sub-Division, then the asset, each with its own dashboard.
- **Role-based access:** six roles, each limited to its jurisdiction on every endpoint.
- **Inspection lifecycle:** field engineers log inspections; condition band, risk index and next maintenance date are recalculated automatically.
- **Predictive maintenance:** a risk index (0 to 100) combining condition (50%), age (18%), traffic (12%), time since inspection (10%) and asset-type criticality (10%), plus a deterioration model giving months to critical, recommended treatment and cost.
- **Project monitoring:** works from sanction through tendering, execution and completion under real scheme names (PMGSY, Mukhyamantri Gram Sadak Yojana, Kisan Path, NABARD RIDF). Works more than 10 points behind plan are flagged.
- **Contractor portal:** progress reports and running account bills, with a verify, approve and pay workflow for engineers.
- **Citizen portal:** map-based issue reporting, automatic routing to the nearest sub-division, public ticket tracking and public statistics.
- **Audit trail:** every inspection, bill decision, progress update and complaint action is logged.

---

## Architecture

```
┌──────────────────────────────┐        ┌──────────────────────────────────────┐
│  React 18 web app            │  JSON  │  Java 17 backend                     │
│  (prebuilt in frontend/dist) │ ─────► │  JDK HTTP server + router            │
│  Leaflet maps, Recharts      │  /api  │  JWT sign-in (HMAC-SHA256)           │
└──────────────────────────────┘        │  Role & jurisdiction scoping         │
                                        │  Health / risk engine                │
                                        │  JDBC                                │
                                        └──────────────────┬───────────────────┘
                                                           │
                                           H2 embedded SQL database (data/)
```

The backend has no framework dependencies: it uses only the Java standard library plus the H2 database driver (`backend/lib/h2-2.2.224.jar`). One process on one port serves both the API and the web app.

### Project layout

```
infratrack/
├── run.bat / run.sh              start the platform
├── build.bat / build.sh          recompile the Java backend
├── backend/
│   ├── infratrack.jar            prebuilt, runnable
│   ├── lib/h2-2.2.224.jar        embedded database
│   ├── pom.xml                   optional, for importing into an IDE
│   └── src/main/java/gov/gujarat/rnb/infratrack/
│       ├── App.java              entry point: database, seed, HTTP server
│       ├── http/                 Router, Req, Json, ApiException
│       ├── db/                   Db (JDBC helper), Schema, Where (query builder)
│       ├── core/                 Auth, User, Scope, Hierarchy, Health, Stats, Seed
│       └── api/                  AuthApi, DashboardApi, AssetApi, ProjectApi, CitizenApi
├── frontend/
│   ├── dist/                     prebuilt web app served by the backend
│   └── src/                      React source (pages, components, styles)
├── Dockerfile, docker-compose.yml
└── README.md
```

### Where to look in the code

| Question a judge might ask | File |
|---|---|
| How does each officer see only their jurisdiction? | `core/Scope.java` |
| How is the risk index and forecast calculated? | `core/Health.java` |
| How are dashboard numbers aggregated? | `core/Stats.java` |
| How is a complaint routed to the right sub-division? | `api/CitizenApi.java` (nearest sub-division by distance) |
| How does login work? | `core/Auth.java` (PBKDF2 password hashing, signed JWT tokens) |
| What does the database look like? | `db/Schema.java` |
| Where does the demo data come from? | `core/Seed.java` |

### Data model

| Table | Purpose |
|---|---|
| regions, divisions, subdivisions | R&B administrative hierarchy |
| assets | The infrastructure registry, with condition, risk and maintenance dates |
| inspections | Field inspection history per asset |
| projects | Works: scheme, contractor, cost, expenditure, progress, schedule |
| progress_updates, bills | Contractor progress reports and running account bills |
| contractors | Registered firms, class and rating |
| complaints | Citizen reports with routing and status |
| users | Accounts with role and jurisdiction |
| audit_logs | Activity trail |

### API endpoints

| Method | Path | Who |
|---|---|---|
| POST | `/api/auth/login` | Everyone |
| GET | `/api/auth/me`, `/api/auth/demo-accounts` | Signed in / everyone |
| GET | `/api/dashboard/summary`, `/alerts`, `/critical-map`, `/api/activity` | Officers |
| GET | `/api/hierarchy/node?level=region&id=3`, `/api/hierarchy/options` | Officers (scoped) |
| GET | `/api/assets`, `/api/assets/geo`, `/api/assets/{id}` | Officers (scoped) |
| POST | `/api/assets/{id}/inspections` | Engineers |
| GET | `/api/projects`, `/api/projects/{id}` | Officers, contractor (own works) |
| POST | `/api/projects/{id}/progress`, `/api/projects/{id}/bills` | Contractor (bills), contractor and engineers (progress) |
| PATCH | `/api/projects/bills/{id}` | Division Engineer and above |
| GET | `/api/analytics/priority` | Officers |
| GET, POST | `/api/complaints` | Signed in (list), anyone (file) |
| PATCH | `/api/complaints/{id}` | Officers |
| GET | `/api/complaints/track/{ticket}`, `/api/complaints/categories` | Anyone |
| GET | `/api/public/stats`, `/api/public/works` | Anyone |

---

## Changing things

- **Backend (Java):** edit files under `backend/src`, then run `build.bat` / `./build.sh` (needs a JDK 17+) and start again with `run.bat`.
- **Frontend (React):** needs Node.js 18+. `cd frontend`, `npm install`, then `npm run dev` for live editing on http://localhost:5173 (it forwards API calls to the Java server on 8080, so keep `run.bat` running). `npm run build` refreshes `frontend/dist`.

## Roadmap beyond the hackathon

- Photo and document uploads on inspections, progress reports and complaints
- Mobile field app with offline inspections for sub-division staff
- Integration with Gujarat's IWDMS / e-tendering and treasury systems for bills
- GIS layers from BISAG for road centrelines and district boundaries
- Machine-learning deterioration models trained on real inspection history
- SMS and WhatsApp updates to citizens on complaint status
- Gujarati language interface
- PostgreSQL for production scale (the SQL is standard; only the JDBC URL changes)
