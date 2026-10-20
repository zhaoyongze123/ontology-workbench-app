import { Rule } from 'antd/lib/form';

/**
 * 验证属性值是否符合类型要求
 * @param value 属性值
 * @param type 属性类型
 * @returns 是否符合类型要求
 */
export const validatePropertyValue = (value: string, type: string) => {
  if (!value && value !== '0' && value !== 'false') return true;

  switch (type) {
    case 'NUMBER':
      return !isNaN(Number(value));
    case 'FLOAT':
      return !isNaN(Number(value));
    case 'BOOLEAN':
      return value === 'true' || value === 'false';
    case 'OBJECT':
      try {
        const parsed = JSON.parse(value);
        // 校验：必须是对象类型（包括数组，但排除 null 和原始类型）
        // 空对象 {} 和空数组 [] 都是有效的
        return parsed !== null && typeof parsed === 'object';
      } catch (e) {
        return false;
      }
    case 'STRING':
    default:
      return true;
  }
};

/**
 * 获取属性类型对应的提示文本
 * @param type 属性类型
 * @returns 提示文本
 */
export const getPlaceholderByType = (type: string) => {
  switch (type) {
    case 'NUMBER':
      return '请输入数字';
    case 'FLOAT':
      return '请输入数字';
    case 'BOOLEAN':
      return '请选择true或false';
    case 'OBJECT':
      return '请输入JSON格式对象，如: {"name":"张三","age":25}';
    case 'STRING':
    default:
      return '请输入属性值';
  }
};

/**
 * 校验名称是否以汉字、字母或下划线开头，且不包含特殊字符
 * @param message 错误提示信息
 * @returns Rule 校验规则
 */
export const validateNameStart = (
  message = '名称必须以汉字、字母或下划线开头',
): Rule => ({
  validator: (_, value) => {
    if (!value) return Promise.resolve();
    // 校验是否以汉字、字母或下划线开头
    const startRegex = /^[\u4e00-\u9fa5a-zA-Z_]/;
    if (!startRegex.test(value)) {
      return Promise.reject(message);
    }
    // 校验是否包含特殊字符（只允许汉字、字母、数字、下划线）
    const specialCharRegex = /^[\u4e00-\u9fa5a-zA-Z0-9_]+$/;
    if (!specialCharRegex.test(value)) {
      return Promise.reject(
        '名称不能包含特殊字符，只允许汉字、字母、数字和下划线',
      );
    }
    return Promise.resolve();
  },
});

/**
 * 校验名称是否由字母组成
 * @param message 错误提示信息
 * @returns Rule 校验规则
 */
export const validateNameIsLetter = (message = '名称只能由字母组成'): Rule => ({
  validator: (_, value) => {
    if (!value) return Promise.resolve();
    const regex = /^[a-zA-Z]+$/;
    if (!regex.test(value)) {
      return Promise.reject(message);
    }
    return Promise.resolve();
  },
});

/**
 * 校验字符串最大长度
 * @param maxLength 最大长度
 * @param message 错误提示信息
 * @returns Rule 校验规则
 */
export const validateMaxLength = (
  maxLength: number,
  message?: string,
): Rule => ({
  validator: (_, value) => {
    if (!value) return Promise.resolve();
    if (value.length > maxLength) {
      return Promise.reject(message || `长度不能超过${maxLength}个字符`);
    }
    return Promise.resolve();
  },
});

/**
 * 校验字符串最大字节数
 * @param maxBytes 最大字节数
 * @param message 错误提示信息
 * @returns Rule 校验规则
 */
export const validateMaxBytes = (
  maxBytes: number,
  message?: string,
): Rule => ({
  validator: (_, value) => {
    if (!value) return Promise.resolve();
    // 计算字符串的字节长度（UTF-8编码）
    const byteLength = new TextEncoder().encode(value).length;
    if (byteLength > maxBytes) {
      return Promise.reject(message || `长度不能超过${maxBytes}个字节`);
    }
    return Promise.resolve();
  },
});

/**
 * 组合校验规则
 * @param rules 校验规则数组
 * @returns Rule 组合后的校验规则
 */
export const combineValidators = (rules: Rule[]): Rule => ({
  validator: async (rule, value) => {
    for (const ruleItem of rules) {
      if (typeof ruleItem === 'object' && 'validator' in ruleItem) {
        try {
          /** 使用类型断言确保 TypeScript 知道 validator 是一个函数 */
          await (ruleItem as any).validator(rule, value);
        } catch (error) {
          return Promise.reject(error);
        }
      }
    }
    return Promise.resolve();
  },
});

/**
 * 名称校验（组合校验规则示例）
 * @returns Rule 校验规则
 */
export const validateGraphName = (): Rule =>
  combineValidators([
    validateNameStart('名称必须以汉字、字母或下划线开头'),
    validateMaxLength(100, '名称限制100字符'),
    validateMaxBytes(63, '名称长度不能超过63字节'),
  ]);

/**
 * 本体关系名称校验
 * @returns Rule 校验规则
 */
export const validateRelationName = (): Rule =>
  combineValidators([
    // validateNameIsLetter('名称只能由字母组成'),
    validateNameStart('名称必须以汉字、字母或下划线开头'),
    validateMaxLength(100, '名称限制100字符'),
    validateMaxBytes(63, '名称长度不能超过63字节'),
  ]);

/**
 * 描述校验（限制500字）
 * @param message 错误提示信息
 * @returns Rule 校验规则
 */
export const validateDescription = (message = '描述限制500字'): Rule =>
  validateMaxLength(500, message);
