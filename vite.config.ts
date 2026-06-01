import { defineConfig, loadEnv } from "vite";
import uni from "@dcloudio/vite-plugin-uni";

// https://vitejs.dev/config/
export default defineConfig(({ command, mode }) => {
  const env = loadEnv(mode, process.cwd(), "");
  const apiBaseUrl = env.VITE_API_BASE_URL?.trim();

  // 只在 production 构建（npm run build:mp-weixin）时强制 HTTPS 检查
  // dev 模式（npm run dev:mp-weixin）可以用 http://localhost / http://127.0.0.1
  if (mode === "production") {
    if (!apiBaseUrl) {
      throw new Error("生产构建必须设置 VITE_API_BASE_URL，例如：VITE_API_BASE_URL=https://api.example.com npm run build:mp-weixin");
    }
    if (!apiBaseUrl.startsWith("https://")) {
      throw new Error("小程序上线请求域名必须使用 HTTPS，请把 VITE_API_BASE_URL 设置为 https:// 开头的线上域名");
    }
  }

  return {
    plugins: [uni()],
  };
});
