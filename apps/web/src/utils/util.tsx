import { fileSave } from 'browser-fs-access';

/**
 * 通用--下载文件
 * @param data  // 待下载的数据
 * @param fileName  //  文件名 ，如 'example.json'
 * @param type  // 文件类型，如 'application/json', 'image/png', 'text/plain'
 * @returns
 */
export function downloadFile(
  data: any,
  fileName: string,
  type: string,
): Promise<void> {
  return new Promise((resolve, reject) => {
    const blob = new Blob([data], { type });
    try {
      fileSave(blob, {
        fileName,
        extensions: [`.${type.split('/')[1]}`],
        description: `${type.split('/')[1]} files`,
      })
        .then(() => resolve())
        .catch(() => {
          reject();
        });
    } catch (error) {
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.download = fileName;
      link.href = url;
      link.click();
      resolve();
    }
  });
}

/**
 * 下载json文件
 * @param data  // 待下载的数据
 * @param fileName  //  文件名 model.json
 * @returns
 */
export function downloadJson(data: any, fileName: string) {
  const fileData = JSON.stringify(data, null, 2);
  const blob = new Blob([fileData], { type: 'application/json' });
  return new Promise((resolve, reject) => {
    /**补救方法 */
    const catchFun = () => {
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.download = fileName;
      link.href = url;
      link.click();
      resolve(true);
    };
    try {
      fileSave(blob, {
        fileName,
        extensions: ['.json'],
        description: 'JSON files',
      })
        .then(() => {
          resolve(true);
        })
        .catch((e) => {
          /** 如果请求时间过长，则fileSave会走catch,是因为浏览器安全策略 */
          if (String(e) && String(e).includes('SecurityError')) {
            catchFun();
          } else {
            reject(false);
          }
        });
    } catch (error) {
      catchFun();
    }
  });
}

/**
 * 下载Xlsx文件
 * @param data  // 待下载的数据
 * @param fileName  //  文件名 model.json
 * @returns
 */
export function downloadXlsx(data: any, fileName: string) {
  const blob = new Blob([data], { type: 'application/vnd.ms-excel' });
  return new Promise((resolve, reject) => {
    /**补救方法 */
    const catchFun = () => {
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.download = fileName;
      link.href = url;
      link.click();
      resolve(true);
    };
    try {
      fileSave(blob, {
        fileName,
        extensions: ['.xlsx'],
        description: 'xlsx files',
      })
        .then(() => {
          resolve(true);
        })
        .catch((e) => {
          /** 如果请求时间过长，则fileSave会走catch,是因为浏览器安全策略 */
          if (String(e) && String(e).includes('SecurityError')) {
            catchFun();
          } else {
            reject(false);
          }
        });
    } catch (error) {
      catchFun();
    }
  });
}

/**
 * 下载PNG文件
 * @param imageData  // 待下载的数据（通常是二进制图像数据）
 * @param fileName  // 文件名 image.png
 */
export function downloadImage(imageData: any, fileName: string): Promise<void> {
  return new Promise((resolve, reject) => {
    const blob = new Blob([imageData], { type: 'image/png' });
    try {
      fileSave(blob, {
        fileName,
        extensions: ['.png'],
        description: 'PNG files',
      })
        .then(() => resolve())
        .catch(() => {
          reject();
        });
    } catch (error) {
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.download = fileName;
      link.href = url;
      link.click();
      resolve();
    }
  });
}

/** 字符串切割，默认最多保留8个,字符串长度是否超过，超过则截取，并在末尾添加... */
export function truncateString(str: any, startNumber: number = 8) {
  if (str?.length > startNumber) {
    return str.substring(0, startNumber) + '...';
  }
  return str;
}

/**
 * 判断对象是否为节点数据结构
 * @param obj 待检查的对象
 * @returns 是否为节点数据
 */
function isNodeData(obj: any): boolean {
  return (
    obj &&
    typeof obj === 'object' &&
    obj.identity !== undefined &&
    obj.labels &&
    Array.isArray(obj.labels) &&
    obj.properties &&
    typeof obj.properties === 'object'
  );
}

/**
 * 判断对象是否为关系数据结构
 * @param obj 待检查的对象
 * @returns 是否为关系数据
 */
function isRelationshipData(obj: any): boolean {
  return (
    obj &&
    typeof obj === 'object' &&
    obj.identity !== undefined &&
    obj.start !== undefined &&
    obj.end !== undefined &&
    obj.type !== undefined &&
    obj.properties &&
    typeof obj.properties === 'object'
  );
}

/**
 * 深度遍历对象或数组，查找所有符合节点或关系数据结构的数据
 * @param data 待遍历的数据
 * @param visited 已访问的对象集合，用于避免循环引用
 * @returns 包含nodes和relationships的对象
 */
function deepExtract(data: any, visited: WeakSet<any> = new WeakSet()): { nodes: any[]; relationships: any[] } {
  const nodes: any[] = [];
  const relationships: any[] = [];
  // 避免循环引用
  if (visited.has(data)) {
    return { nodes, relationships };
  }
  // 如果是基本类型，直接返回
  if (data === null || data === undefined || typeof data !== 'object') {
    return { nodes, relationships };
  }
  // 标记当前对象为已访问
  visited.add(data);
  try {
    // 如果当前对象本身就是节点数据
    if (isNodeData(data)) {
      nodes.push(data);
      return { nodes, relationships };
    }
    // 如果当前对象本身就是关系数据
    if (isRelationshipData(data)) {
      relationships.push(data);
      return { nodes, relationships };
    }
    // 如果是数组，遍历每个元素
    if (Array.isArray(data)) {
      data.forEach((item) => {
        const result = deepExtract(item, visited);
        nodes.push(...result.nodes);
        relationships.push(...result.relationships);
      });
    } else {
      // 如果是对象，遍历每个属性值
      Object.values(data).forEach((value) => {
        const result = deepExtract(value, visited);
        nodes.push(...result.nodes);
        relationships.push(...result.relationships);
      });
    }
  } finally {
    // 清理访问标记（可选，WeakSet会自动处理）
    visited.delete(data);
  }
  return { nodes, relationships };
}

/**
 * 提取节点和关系数据的通用方法
 * 支持深度解构多层嵌套的对象和数组，自动识别节点和关系数据结构
 * @param data cypher查询返回的数据数组或任何嵌套结构
 * @returns 包含nodes和relationships的对象
 */
export function extractNodesAndRelationships(data: any[]): { nodes: any[]; relationships: any[] } {
  const allNodes: any[] = [];
  const allRelationships: any[] = [];
  const nodeIdentitySet = new Set<string>();
  const relationshipIdentitySet = new Set<string>();
  // 遍历输入数据
  data.forEach((item) => {
    const result = deepExtract(item);
    // 去重并收集节点
    result.nodes.forEach((node) => {
      if (node.identity && !nodeIdentitySet.has(node.identity)) {
        nodeIdentitySet.add(node.identity);
        allNodes.push(node);
      }
    });
    // 去重并收集关系
    result.relationships.forEach((rel) => {
      if (rel.identity && !relationshipIdentitySet.has(rel.identity)) {
        relationshipIdentitySet.add(rel.identity);
        allRelationships.push(rel);
      }
    });
  });
  return { 
    nodes: allNodes, 
    relationships: allRelationships 
  };
}
