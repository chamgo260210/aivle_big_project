/**
 * 와이어프레임 전용 진입점 — `npm run dev` 뒤 http://localhost:5173/wireframe.html
 *
 * <p><b>백엔드도 로그인도 필요 없다.</b> 「사업 검증」 → 「다듬어진 컨셉」 두 화면
 * (계획서 2-3 · 3-5)의 배치를 미리 본다. 목업
 * (`docs/mockups/business-validation.html`)에서 가져오는 것은 <b>순서</b>뿐이고,
 * 색·부품·간격은 전부 우리 것(`tokens.css`·`ui.css`·`market.css`)이다.
 *
 * <p>⚠ 데이터는 <b>창작</b>이다(`sample.js`). 주제를 하나로 묶으려고 지어냈다 —
 * 골든 픽스처를 쓰면 화면 1의 숫자와 화면 2의 컨셉 문장이 서로 다른 사업을 말한다.
 *
 * <p>⚠ 이것은 제품 라우트가 아니다. `AppRouter.jsx` 를 건드리지 않으려고 별도 Vite
 * 진입점으로 뒀다. 2-3 을 실제로 만들 때는 `features/market/BusinessValidationPage.jsx`
 * 를 만든다.
 */
import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';

import '../shared/styles/global.css';
import '../features/market/market.css';
import './wireframe.css';

import BusinessValidationWireframe from './BusinessValidationWireframe.jsx';

createRoot(document.getElementById('root'))
  .render(<StrictMode><BusinessValidationWireframe /></StrictMode>);
