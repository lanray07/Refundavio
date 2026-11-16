import { billingIdentifiers, verifyAmazonReceipt } from './amazon-rvs.mjs';

function reply(status, body = {}) {
  return Response.json(body, { status, headers: { 'Cache-Control': 'no-store' } });
}

export default {
  async fetch(request, env) {
    if (request.method !== 'POST' || new URL(request.url).pathname !== '/verify') return reply(404);
    if (request.headers.get('Content-Type')?.split(';')[0] !== 'application/json') return reply(415);
    const secret = env.AMAZON_RVS_SHARED_SECRET;
    if (!secret) return reply(503);

    const reader = request.body?.getReader();
    if (!reader) return reply(400);
    const decoder = new TextDecoder();
    let length = 0;
    let body = '';
    let ids;
    try {
      for (let chunk = await reader.read(); !chunk.done; chunk = await reader.read()) {
        length += chunk.value.length;
        if (length > 8192) {
          void reader.cancel().catch(() => {});
          return reply(413);
        }
        body += decoder.decode(chunk.value, { stream: true });
      }
      ids = billingIdentifiers(JSON.parse(body + decoder.decode()));
      if (!ids) return reply(400);
    } catch {
      return reply(400);
    }

    try {
      const quota = await env.VERIFICATION_LIMIT.limit({ key: 'refundavio-amazon-rvs' });
      if (!quota.success) return reply(429);
      const active = await verifyAmazonReceipt(ids, { sandbox: false, secret });
      return reply(200, { active });
    } catch {
      return reply(502);
    }
  },
};
