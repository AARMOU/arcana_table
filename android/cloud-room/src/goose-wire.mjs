// B140 gzip/base64 envelope, bounded BEFORE decompression allocation.
export async function unpack(data) {
  if (typeof data !== 'string' || data.length > 400000) throw Error('packet-size');
  const raw = Uint8Array.from(atob(data), c => c.charCodeAt(0));
  const stream = new Blob([raw]).stream().pipeThrough(new DecompressionStream('gzip'));
  const reader = stream.getReader();
  const chunks = []; let size = 0;
  while (true) {
    const {done, value} = await reader.read();
    if (done) break;
    size += value.length;
    if (size > 512000) { await reader.cancel(); throw Error('packet-size'); }
    chunks.push(value);
  }
  const bytes = new Uint8Array(size); let offset = 0;
  for (const chunk of chunks) { bytes.set(chunk, offset); offset += chunk.length; }
  const packet = JSON.parse(new TextDecoder('utf-8', {fatal:true}).decode(bytes));
  if (!packet || Array.isArray(packet) || typeof packet !== 'object') throw Error('packet-shape');
  return packet;
}
export async function pack(packet) {
  const bytes = new TextEncoder().encode(JSON.stringify(packet));
  if (bytes.length > 512000) throw Error('packet-size');
  const stream = new Blob([bytes]).stream().pipeThrough(new CompressionStream('gzip'));
  const compressed = new Uint8Array(await new Response(stream).arrayBuffer());
  let text = ''; for (const byte of compressed) text += String.fromCharCode(byte);
  const result = btoa(text);
  if (result.length > 400000) throw Error('packet-size');
  return result;
}
