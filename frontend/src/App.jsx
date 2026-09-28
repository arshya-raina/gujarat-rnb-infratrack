import { Routes, Route, Navigate } from "react-router-dom";
import { useAuth, isOfficer, homeFor } from "./lib.jsx";
import { Loading } from "./components/ui.jsx";
import Layout from "./components/Layout.jsx";
import Login from "./pages/Login.jsx";
import CommandCenter from "./pages/CommandCenter.jsx";
import Explorer from "./pages/Explorer.jsx";
import Assets from "./pages/Assets.jsx";
import AssetDetail from "./pages/AssetDetail.jsx";
import Projects from "./pages/Projects.jsx";
import ProjectDetail from "./pages/ProjectDetail.jsx";
import Complaints from "./pages/Complaints.jsx";
import Priority from "./pages/Priority.jsx";
import PublicPortal from "./pages/PublicPortal.jsx";

function Guard({ allow, children }) {
  const { user } = useAuth();
  if (!user) return <Navigate to="/login" replace />;
  if (!allow(user)) return <Navigate to={homeFor(user)} replace />;
  return children;
}

const officer = (u) => isOfficer(u);
const works = (u) => isOfficer(u) || u.role === "contractor";
const signedIn = (u) => u.role !== "contractor";

export default function App() {
  const { ready, user } = useAuth();
  if (!ready) return <Loading label="Starting InfraTrack" />;
  return (
    <Routes>
      <Route path="/login" element={user ? <Navigate to={homeFor(user)} replace /> : <Login />} />
      <Route element={<Layout />}>
        <Route path="/" element={<Guard allow={officer}><CommandCenter /></Guard>} />
        <Route path="/explore" element={<Guard allow={officer}><Explorer /></Guard>} />
        <Route path="/explore/:level/:id" element={<Guard allow={officer}><Explorer /></Guard>} />
        <Route path="/assets" element={<Guard allow={officer}><Assets /></Guard>} />
        <Route path="/assets/:id" element={<Guard allow={officer}><AssetDetail /></Guard>} />
        <Route path="/projects" element={<Guard allow={works}><Projects /></Guard>} />
        <Route path="/projects/:id" element={<Guard allow={works}><ProjectDetail /></Guard>} />
        <Route path="/complaints" element={<Guard allow={signedIn}><Complaints /></Guard>} />
        <Route path="/priority" element={<Guard allow={officer}><Priority /></Guard>} />
        <Route path="/public" element={<PublicPortal />} />
      </Route>
      <Route path="*" element={<Navigate to={homeFor(user)} replace />} />
    </Routes>
  );
}
