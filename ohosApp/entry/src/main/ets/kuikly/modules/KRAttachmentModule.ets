import { KRAny, KuiklyRenderBaseModule, KuiklyRenderCallback } from '@kuikly-open/render';
import { common, abilityAccessCtrl, Permissions } from '@kit.AbilityKit';
import { camera, cameraPicker } from '@kit.CameraKit';
import { photoAccessHelper } from '@kit.MediaLibraryKit';
import { fileIo as fs, fileUri, picker } from '@kit.CoreFileKit';
import { http } from '@kit.NetworkKit';
import { util } from '@kit.ArkTS';
import { BusinessError } from '@kit.BasicServicesKit';

export class KRAttachmentModule extends KuiklyRenderBaseModule {
  static readonly MODULE_NAME = 'KRAttachmentModule';

  syncMode(): boolean {
    return false;
  }

  call(method: string, params: KRAny, callback: KuiklyRenderCallback | null): KRAny {
    const json = asRecord(params);
    switch (method) {
      case 'open':
        this.open(String(json['source'] ?? 'file'), callback);
        break;
      case 'readFile':
        this.readFile(String(json['path'] ?? ''), callback);
        break;
      case 'deleteFile':
        callback?.(JSON.stringify({ success: this.removeFile(String(json['path'] ?? '')) }));
        break;
      case 'fileExists':
        callback?.(JSON.stringify({ exists: fs.accessSync(String(json['path'] ?? '')) }));
        break;
      case 'upload':
        this.upload(json, callback);
        break;
      case 'deleteRemote':
        this.deleteRemote(json, callback);
        break;
      default:
        break;
    }
    return null;
  }

  private abilityContext(): common.UIAbilityContext | undefined {
    return AppStorage.get<common.UIAbilityContext>('abilityContext');
  }

  private async open(source: string, callback: KuiklyRenderCallback | null): Promise<void> {
    const context = this.abilityContext();
    if (!context) {
      callback?.(JSON.stringify({ cancelled: false, error: '当前页面无法选择附件' }));
      return;
    }
    try {
      if (source === 'camera') {
        const granted = await this.ensurePermission(context, 'ohos.permission.CAMERA');
        if (!granted) {
          callback?.(JSON.stringify({ cancelled: false, error: '未获得相机权限' }));
          return;
        }
        const profile: cameraPicker.PickerProfile = {
          cameraPosition: camera.CameraPosition.CAMERA_POSITION_BACK
        };
        const picked = await cameraPicker.pick(context, [cameraPicker.PickerMediaType.PHOTO], profile);
        if (picked.resultCode !== 0 || !picked.resultUri) {
          callback?.(JSON.stringify({ cancelled: true }));
          return;
        }
        const item = this.copyUri(context, picked.resultUri, '拍摄图片.jpg', 'CAMERA');
        callback?.(JSON.stringify({ cancelled: false, attachments: item ? [item] : [] }));
        return;
      }
      if (source === 'photo_library') {
        const photoPicker = new photoAccessHelper.PhotoViewPicker();
        const selected = await photoPicker.select({
          MIMEType: photoAccessHelper.PhotoViewMIMETypes.IMAGE_TYPE,
          maxSelectNumber: 9
        });
        const attachments = (selected.photoUris ?? []).map((uri, index) =>
          this.copyUri(context, uri, `相册图片${index + 1}.jpg`, 'PHOTO_LIBRARY')).filter((item) => item !== undefined);
        callback?.(JSON.stringify({ cancelled: attachments.length === 0, attachments }));
        return;
      }
      const documentPicker = new picker.DocumentViewPicker(context);
      const uris = await documentPicker.select({ maxSelectNumber: 9 });
      const attachments = (uris ?? []).map((uri) => {
        const name = decodeURIComponent(uri.split('/').pop() ?? '附件');
        return this.copyUri(context, uri, name, 'FILE');
      }).filter((item) => item !== undefined);
      callback?.(JSON.stringify({ cancelled: attachments.length === 0, attachments }));
    } catch (error) {
      const err = error as BusinessError;
      callback?.(JSON.stringify({ cancelled: false, error: err.message ?? '选择附件失败' }));
    }
  }

  private async ensurePermission(context: common.UIAbilityContext, permission: Permissions): Promise<boolean> {
    const manager = abilityAccessCtrl.createAtManager();
    const status = manager.checkAccessTokenSync(context.applicationInfo.accessTokenId, permission);
    if (status === abilityAccessCtrl.GrantStatus.PERMISSION_GRANTED) {
      return true;
    }
    const result = await manager.requestPermissionsFromUser(context, [permission]);
    return result.authResults[0] === abilityAccessCtrl.GrantStatus.PERMISSION_GRANTED;
  }

  private copyUri(context: common.UIAbilityContext, uri: string, displayName: string, source: string): AttachmentItem | undefined {
    try {
      const dir = `${context.filesDir}/attachments`;
      if (!fs.accessSync(dir)) {
        fs.mkdirSync(dir);
      }
      const dest = `${dir}/${Date.now()}-${displayName}`;
      const srcPath = uri.startsWith('file://') ? new fileUri.FileUri(uri).path : uri;
      fs.copyFileSync(srcPath, dest);
      const mime = mimeOf(displayName);
      const kind = mime.startsWith('image/') ? 'IMAGE' : 'DOCUMENT';
      const item: AttachmentItem = {
        id: dest.split('/').pop()?.replace(/\.[^.]+$/, '') ?? `${Date.now()}`,
        displayName,
        mimeType: mime,
        byteSize: fs.statSync(dest).size,
        localPath: dest,
        source,
        kind,
        status: 'READY',
        thumbnailPath: kind === 'IMAGE' ? dest : ''
      };
      return item;
    } catch (_error) {
      return undefined;
    }
  }

  private readFile(path: string, callback: KuiklyRenderCallback | null): void {
    try {
      if (!fs.accessSync(path)) {
        callback?.(JSON.stringify({ success: false, error: '文件不存在' }));
        return;
      }
      const size = fs.statSync(path).size;
      if (size > 12 * 1024 * 1024) {
        callback?.(JSON.stringify({ success: false, error: '附件过大，请换一张较小的图片' }));
        return;
      }
      const mime = mimeOf(path);
      const file = fs.openSync(path, fs.OpenMode.READ_ONLY);
      const buffer = new ArrayBuffer(size);
      fs.readSync(file.fd, buffer);
      fs.closeSync(file);
      if (mime.startsWith('image/')) {
        const encoded = new util.Base64Helper().encodeToStringSync(new Uint8Array(buffer));
        callback?.(JSON.stringify({ success: true, dataUrl: `data:${mime};base64,${encoded}` }));
        return;
      }
      if (mime.startsWith('text/') || mime === 'application/json') {
        const decoder = util.TextDecoder.create('utf-8');
        const text = decoder.decodeToString(new Uint8Array(buffer));
        callback?.(JSON.stringify({ success: true, text }));
        return;
      }
      callback?.(JSON.stringify({ success: true }));
    } catch (error) {
      const err = error as BusinessError;
      callback?.(JSON.stringify({ success: false, error: err.message ?? '读取附件失败' }));
    }
  }

  private removeFile(path: string): boolean {
    try {
      if (fs.accessSync(path)) {
        fs.unlinkSync(path);
        return true;
      }
    } catch (_error) {
    }
    return false;
  }

  private async upload(json: Record<string, Object>, callback: KuiklyRenderCallback | null): Promise<void> {
    const path = String(json['path'] ?? '');
    const url = String(json['url'] ?? '');
    try {
      if (!fs.accessSync(path)) {
        callback?.(JSON.stringify({ success: false, error: '文件不存在' }));
        return;
      }
      const boundary = `----StockChat${Date.now()}`;
      const file = fs.openSync(path, fs.OpenMode.READ_ONLY);
      const size = fs.statSync(path).size;
      const fileBuf = new ArrayBuffer(size);
      fs.readSync(file.fd, fileBuf);
      fs.closeSync(file);
      const encoder = new util.TextEncoder();
      const chunks: Uint8Array[] = [];
      const fields = asRecord(json['fields']);
      Object.keys(fields).forEach((key) => {
        chunks.push(encoder.encodeInto(`--${boundary}\r\nContent-Disposition: form-data; name="${key}"\r\n\r\n${String(fields[key])}\r\n`));
      });
      const filename = path.split('/').pop() ?? 'file';
      chunks.push(encoder.encodeInto(`--${boundary}\r\nContent-Disposition: form-data; name="file"; filename="${filename}"\r\nContent-Type: application/octet-stream\r\n\r\n`));
      chunks.push(new Uint8Array(fileBuf));
      chunks.push(encoder.encodeInto(`\r\n--${boundary}--\r\n`));
      const body = concat(chunks);
      const headers = asStringMap(json['headers']);
      headers['Content-Type'] = `multipart/form-data; boundary=${boundary}`;
      const client = http.createHttp();
      const response = await client.request(url, {
        method: http.RequestMethod.POST,
        header: headers,
        extraData: body.buffer,
        expectDataType: http.HttpDataType.STRING,
        connectTimeout: 60000,
        readTimeout: 60000
      });
      client.destroy();
      const ok = response.responseCode >= 200 && response.responseCode < 300;
      const text = String(response.result ?? '');
      callback?.(JSON.stringify({
        success: ok,
        body: text,
        error: ok ? '' : (text || `上传失败 ${response.responseCode}`)
      }));
    } catch (error) {
      const err = error as BusinessError;
      callback?.(JSON.stringify({ success: false, error: err.message ?? '上传失败' }));
    }
  }

  private async deleteRemote(json: Record<string, Object>, callback: KuiklyRenderCallback | null): Promise<void> {
    try {
      const client = http.createHttp();
      const response = await client.request(String(json['url'] ?? ''), {
        method: http.RequestMethod.DELETE,
        header: asStringMap(json['headers']),
        connectTimeout: 15000,
        readTimeout: 15000
      });
      client.destroy();
      callback?.(JSON.stringify({ success: response.responseCode >= 200 && response.responseCode < 300 }));
    } catch (_error) {
      callback?.(JSON.stringify({ success: false }));
    }
  }
}

function asRecord(value: Object | undefined | null): Record<string, Object> {
  if (typeof value === 'string') {
    try {
      return JSON.parse(value) as Record<string, Object>;
    } catch (_error) {
      return {};
    }
  }
  if (value && typeof value === 'object') {
    return value as Record<string, Object>;
  }
  return {};
}

function asStringMap(value: Object | undefined): Record<string, string> {
  const record = asRecord(value);
  const map: Record<string, string> = {};
  Object.keys(record).forEach((key) => {
    map[key] = String(record[key]);
  });
  return map;
}

function mimeOf(name: string): string {
  const ext = name.split('.').pop()?.toLowerCase() ?? '';
  if (ext === 'png') return 'image/png';
  if (ext === 'jpg' || ext === 'jpeg') return 'image/jpeg';
  if (ext === 'gif') return 'image/gif';
  if (ext === 'webp') return 'image/webp';
  if (ext === 'txt' || ext === 'md' || ext === 'csv') return 'text/plain';
  if (ext === 'json') return 'application/json';
  if (ext === 'pdf') return 'application/pdf';
  if (ext === 'doc') return 'application/msword';
  if (ext === 'docx') return 'application/vnd.openxmlformats-officedocument.wordprocessingml.document';
  return 'application/octet-stream';
}

interface AttachmentItem {
  id: string;
  displayName: string;
  mimeType: string;
  byteSize: number;
  localPath: string;
  source: string;
  kind: string;
  status: string;
  thumbnailPath: string;
}

function concat(chunks: Uint8Array[]): Uint8Array {
  const total = chunks.reduce((sum, chunk) => sum + chunk.length, 0);
  const output = new Uint8Array(total);
  let offset = 0;
  chunks.forEach((chunk) => {
    output.set(chunk, offset);
    offset += chunk.length;
  });
  return output;
}
