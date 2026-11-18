import { Navigate, Route, Routes } from 'react-router'
import { Shell } from './app/Shell'
import { ChatScreen } from './screens/ChatScreen'
import { ExamsScreen } from './screens/ExamsScreen'
import { HomeScreen } from './screens/HomeScreen'
import { LevelsScreen } from './screens/LevelsScreen'

export function App() {
  return (
    <Routes>
      <Route element={<Shell />}>
        <Route index element={<HomeScreen />} />
        <Route path="conversa" element={<ChatScreen />} />
        <Route path="provas" element={<ExamsScreen />} />
        <Route path="niveis" element={<LevelsScreen />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  )
}
