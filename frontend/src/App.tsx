import { Navigate, Route, Routes } from 'react-router'

import { AdminAccountsPage } from './presentation/features/admin/view/AdminAccountsPage'
import { LoginPage } from './presentation/features/auth/view/LoginPage'
import { SignupPage } from './presentation/features/auth/view/SignupPage'
import { useRestoreAuthSession } from './presentation/features/auth/viewmodel/useAuthSessionViewModel'
import { ChatPage } from './presentation/features/chat/view/ChatPage'
import { SupportProgramDetailPage } from './presentation/features/chat/view/SupportProgramDetailPage'
import { MyRecruitmentPostsPage } from './presentation/features/recruitment/view/MyRecruitmentPostsPage'
import { RecruitmentPostDetailPage } from './presentation/features/recruitment/view/RecruitmentPostDetailPage'
import { RecruitmentPostFormPage } from './presentation/features/recruitment/view/RecruitmentPostFormPage'
import { RecruitmentPostListPage } from './presentation/features/recruitment/view/RecruitmentPostListPage'
import { ReduxSampleItemPage } from './presentation/features/sample-item/view/ReduxSampleItemPage'
import { SampleItemPage } from './presentation/features/sample-item/view/SampleItemPage'

/** GovBiz의 첫 진입점은 공고를 찾는 채팅 화면입니다. 로그인은 선택이며 진입 시 저장된 세션을 복원합니다. */
function App() {
  useRestoreAuthSession()

  return (
    <Routes>
      <Route path="/" element={<ChatPage />} />
      <Route path="/login" element={<LoginPage />} />
      <Route path="/signup" element={<SignupPage />} />
      <Route
        path="/support-programs/detail"
        element={<SupportProgramDetailPage />}
      />
      <Route path="/partners" element={<RecruitmentPostListPage />} />
      <Route path="/partners/new" element={<RecruitmentPostFormPage />} />
      <Route path="/partners/mine" element={<MyRecruitmentPostsPage />} />
      <Route path="/partners/:postId" element={<RecruitmentPostDetailPage />} />
      <Route path="/partners/:postId/edit" element={<RecruitmentPostFormPage />} />
      <Route path="/admin" element={<Navigate replace to="/admin/accounts" />} />
      <Route path="/admin/accounts" element={<AdminAccountsPage />} />
      <Route path="/examples/sample-item/hook" element={<SampleItemPage />} />
      <Route path="/examples/sample-item/redux" element={<ReduxSampleItemPage />} />
      <Route path="*" element={<Navigate replace to="/" />} />
    </Routes>
  )
}

export default App
