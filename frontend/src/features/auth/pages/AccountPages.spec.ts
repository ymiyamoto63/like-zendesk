import { flushPromises, mount } from "@vue/test-utils";
import { createPinia } from "pinia";
import PrimeVue from "primevue/config";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { Component } from "vue";
import { createMemoryHistory, createRouter } from "vue-router";
import AcceptInvitationPage from "./AcceptInvitationPage.vue";
import RegisterPage from "./RegisterPage.vue";
import ResetPasswordPage from "./ResetPasswordPage.vue";
import VerifyEmailPage from "./VerifyEmailPage.vue";

function json(status: number, body?: unknown) {
  return new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

const tenant = {
  slug: "acme",
  name: "Acme 社",
  requesterSignup: "domain_restricted",
};

/** `api` は `METHOD /path` をキーにした応答。それ以外は 404。 */
async function mountAt(
  page: Component,
  pattern: string,
  path: string,
  api: Record<string, () => Response>,
) {
  document.cookie = "XSRF-TOKEN=t";
  const calls: { key: string; body: unknown }[] = [];
  vi.stubGlobal(
    "fetch",
    vi.fn(async (input: Request) => {
      const key = `${input.method} ${new URL(input.url).pathname}`;
      calls.push({
        key,
        body: input.method === "GET" ? undefined : await input.clone().json(),
      });
      if (key === "GET /api/t/acme") return json(200, tenant);
      return api[key]?.() ?? json(404, { code: "not_found" });
    }),
  );
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: pattern, component: page },
      { path: "/t/:slug/agent", component: { template: "<p>agent</p>" } },
      { path: "/t/:slug/login", component: { template: "<p>login</p>" } },
      { path: "/t/:slug/register", component: { template: "<p>register</p>" } },
      { path: "/t/:slug", component: { template: "<p>portal</p>" } },
      {
        path: "/:pathMatch(.*)*",
        name: "not-found",
        component: { template: "<p>404</p>" },
      },
    ],
  });
  await router.push(path);
  const wrapper = mount(page, {
    global: { plugins: [createPinia(), router, PrimeVue] },
  });
  await flushPromises();
  return { wrapper, router, calls };
}

const me = {
  id: "u1",
  name: "鈴木",
  email: "suzuki@example.com",
  role: "agent",
  groups: [],
};

describe("RegisterPage", () => {
  afterEach(() => vi.unstubAllGlobals());

  async function register(response: Response) {
    const mounted = await mountAt(
      RegisterPage,
      "/t/:slug/register",
      "/t/acme/register",
      {
        "POST /api/t/acme/requesters": () => response,
      },
    );
    await mounted.wrapper.get("#name").setValue("鈴木");
    await mounted.wrapper.get("#email").setValue("suzuki@example.com");
    await mounted.wrapper.get("#password").setValue("secret-pass");
    await mounted.wrapper.get("form").trigger("submit");
    await flushPromises();
    return mounted;
  }

  it("asks for a work address when the tenant restricts domains", async () => {
    const { wrapper } = await mountAt(
      RegisterPage,
      "/t/:slug/register",
      "/t/acme/register",
      {},
    );
    expect(wrapper.text()).toContain(
      "所属先のメールアドレスで登録してください。",
    );
  });

  it("tells the user to open the mailed link", async () => {
    const { wrapper } = await register(json(202));
    expect(wrapper.text()).toContain(
      "suzuki@example.com に確認メールを送りました。",
    );
  });

  it("explains a rejected domain", async () => {
    const { wrapper } = await register(
      json(422, { code: "requester.domain_not_allowed" }),
    );
    expect(wrapper.text()).toContain("このメールアドレスでは登録できません。");
  });
});

describe("VerifyEmailPage", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("opens the portal once the address is verified", async () => {
    const { router, calls } = await mountAt(
      VerifyEmailPage,
      "/t/:slug/verify-email/:token",
      "/t/acme/verify-email/tok",
      {
        "POST /api/t/acme/email-verifications": () =>
          json(200, { ...me, role: "requester" }),
      },
    );
    expect(
      calls.find((c) => c.key === "POST /api/t/acme/email-verifications")?.body,
    ).toEqual({ token: "tok" });
    expect(router.currentRoute.value.fullPath).toBe("/t/acme");
  });

  it("explains an expired link", async () => {
    const { wrapper } = await mountAt(
      VerifyEmailPage,
      "/t/:slug/verify-email/:token",
      "/t/acme/verify-email/old",
      {
        "POST /api/t/acme/email-verifications": () =>
          json(404, { code: "token.invalid" }),
      },
    );
    expect(wrapper.text()).toContain(
      "リンクが無効か、有効期限が切れています。",
    );
  });
});

describe("AcceptInvitationPage", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("shows the invited address and opens the agent pages after accepting", async () => {
    const { wrapper, router, calls } = await mountAt(
      AcceptInvitationPage,
      "/t/:slug/invitations/:token",
      "/t/acme/invitations/tok",
      {
        "GET /api/t/acme/invitations/tok": () =>
          json(200, { email: "suzuki@example.com" }),
        "POST /api/t/acme/invitations/tok/accept": () => json(200, me),
      },
    );
    expect(wrapper.text()).toContain("suzuki@example.com として参加します。");
    await wrapper.get("#name").setValue("鈴木");
    await wrapper.get("#password").setValue("secret-pass");
    await wrapper.get("form").trigger("submit");
    await flushPromises();
    expect(calls.at(-1)?.body).toEqual({
      name: "鈴木",
      password: "secret-pass",
    });
    expect(router.currentRoute.value.fullPath).toBe("/t/acme/agent");
  });

  it("hides the form when the invitation is no longer valid", async () => {
    const { wrapper } = await mountAt(
      AcceptInvitationPage,
      "/t/:slug/invitations/:token",
      "/t/acme/invitations/old",
      {},
    );
    expect(wrapper.find("form").exists()).toBe(false);
    expect(wrapper.text()).toContain(
      "管理者に招待を出し直してもらってください。",
    );
  });
});

describe("ResetPasswordPage", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("sets the new password and points to the login page", async () => {
    const { wrapper, calls } = await mountAt(
      ResetPasswordPage,
      "/t/:slug/password-reset/:token",
      "/t/acme/password-reset/tok",
      {
        "PUT /api/t/acme/password-resets/tok": () =>
          new Response(null, { status: 204 }),
      },
    );
    await wrapper.get("#password").setValue("new-password");
    await wrapper.get("form").trigger("submit");
    await flushPromises();
    expect(calls.at(-1)?.body).toEqual({ password: "new-password" });
    expect(wrapper.text()).toContain("パスワードを変更しました。");
  });
});
