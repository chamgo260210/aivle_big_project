import process from 'node:process'
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  // `npm run dev` 로 :5173 을 띄우면 **저장하는 즉시** 화면이 바뀐다(HMR).
  // 컨테이너(:3000)는 nginx 가 빌드 산출물을 주는 곳이라 고칠 때마다 재빌드가 필요하다 —
  // 화면을 여러 번 다듬는 동안에는 :5173 을 쓴다.
  //
  // ⚠ 프록시 대상이 backend 가 아니라 **:3000(nginx)** 이다. compose 가 백엔드 포트를
  //    호스트에 열지 않아서(`docker compose ps` 로 확인) localhost:8080 은 닿지 않는다.
  //    nginx 가 이미 /api 를 backend 로 넘기므로 그 길을 그대로 빌려 쓴다.
  //
  //    ⚠ `compose.dev.yaml` 을 얹으면 backend 가 **:8080 에 직접 열린다**. 그때는
  //       nginx 를 거칠 이유가 없고 frontend 컨테이너를 아예 안 띄워도 된다:
  //         $env:VITE_PROXY_TARGET = 'http://localhost:8080'; npm.cmd run dev
  //       기본값은 바꾸지 않는다 — 겹침 파일 없이 쓰는 사람이 조용히 끊긴다.
  server: {
    proxy: {
      '/api': {
        target: process.env.VITE_PROXY_TARGET || 'http://localhost:3000',
        changeOrigin: true,
      },
    },
    // `wireframe.html` 이 저장소 공용 골든 픽스처(ai/tests/fixtures/...)를 **그대로**
    // 읽는다. 사본을 두면 갈라지므로 상위 폴더를 열어 둔다. dev 서버에서만 유효하고
    // 빌드 산출물에는 영향이 없다.
    fs: { allow: ['..'] },
  },
  test: {
    environment: 'jsdom',
    setupFiles: './src/test/setupTests.js',
    css: true,
    // 기본 10초로는 **부하에 따라 결과가 바뀐다.** LandingPage 18개는 단독 실행에서
    // 전부 통과하지만(14.7초), 전체 스위트를 병렬로 돌리면 몇 개가 10초를 넘겨 실패한다.
    // 그러면 test:baseline 의 allowlist 가 실행마다 달라져 게이트가 무작위로 뒤집힌다 —
    // 「깨진 것」과 「느린 것」을 가르지 못하는 게이트는 아무것도 못 잡는다.
    testTimeout: 30000,
    hookTimeout: 30000,
  },
})
