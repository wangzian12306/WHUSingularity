/**
 * 可选 HTTP 参数：设置环境变量 HTTP_REQ_TIMEOUT（如 120s）可拉长单次请求超时。
 * 注意：dial/read「i/o timeout」多因对端慢或队列满，加超时只会「少报错」不解决吞吐瓶颈。
 */
export function postJsonParams(extra = {}) {
  const out = { ...extra };
  const t = __ENV.HTTP_REQ_TIMEOUT;
  if (t != null && String(t).trim() !== '') {
    out.timeout = String(t).trim();
  }
  return out;
}
