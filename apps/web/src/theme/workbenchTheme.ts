import type { ThemeConfig } from 'antd';

/**
 * Workbench 设计变量适配层。
 * CSS 变量保留样例中的 OKLCH 值；Ant Design 使用等效中性色，避免颜色生成器
 * 无法解析 OKLCH，同时让菜单、链接和按钮共用同一套视觉事实源。
 */
export const workbenchCssVariables = {
  '--wb-background': 'oklch(1 0 0)',
  '--wb-foreground': 'oklch(0.145 0 0)',
  '--wb-surface': 'oklch(1 0 0)',
  '--wb-primary': 'oklch(0.205 0 0)',
  '--wb-primary-foreground': 'oklch(0.985 0 0)',
  '--wb-secondary': 'oklch(0.97 0 0)',
  '--wb-muted': 'oklch(0.97 0 0)',
  '--wb-muted-foreground': 'oklch(0.556 0 0)',
  '--wb-border': 'oklch(0.922 0 0)',
  '--wb-input': 'oklch(0.922 0 0)',
  '--wb-ring': 'oklch(0.708 0 0)',
  '--wb-sidebar': 'oklch(0.985 0 0)',
  '--wb-sidebar-foreground': 'oklch(0.145 0 0)',
  '--wb-sidebar-primary': 'oklch(0.205 0 0)',
  '--wb-sidebar-accent': '#e8e8e8',
  '--wb-sidebar-border': 'oklch(0.922 0 0)',
  '--wb-font-sans':
    "'Inter', ui-sans-serif, system-ui, sans-serif, 'Apple Color Emoji', 'Segoe UI Emoji', 'Segoe UI Symbol', 'Noto Color Emoji'",
  '--wb-font-mono':
    'ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace',
  '--wb-radius': '10px',
  '--wb-radius-control': '8px',
  '--wb-radius-card': '14px',
  '--wb-page-margin': '16px',
  '--wb-card-padding': '16px',
  '--wb-form-gap': '16px',
  '--wb-action-gap': '8px',
} as const;

/** Ant Design 5 主题值与上方 CSS 变量按样例的中性色阶一一对应。 */
export const workbenchAntTheme: ThemeConfig = {
  token: {
    colorPrimary: '#1c1c1c',
    colorPrimaryHover: '#383838',
    colorPrimaryActive: '#0a0a0a',
    colorInfo: '#1c1c1c',
    colorLink: '#1c1c1c',
    colorLinkHover: '#404040',
    colorLinkActive: '#0a0a0a',
    colorText: '#0a0a0a',
    colorTextSecondary: '#737373',
    colorTextTertiary: '#8c8c8c',
    colorBgBase: '#ffffff',
    colorBgLayout: '#ffffff',
    colorBgContainer: '#ffffff',
    colorBgElevated: '#ffffff',
    colorBorder: '#e5e5e5',
    colorBorderSecondary: '#e5e5e5',
    colorFillAlter: '#f5f5f5',
    colorFillSecondary: '#f5f5f5',
    fontFamily: workbenchCssVariables['--wb-font-sans'],
    fontFamilyCode: workbenchCssVariables['--wb-font-mono'],
    fontSize: 14,
    fontSizeHeading1: 20,
    fontSizeHeading2: 18,
    fontSizeHeading3: 16,
    fontSizeHeading4: 14,
    lineHeight: 1.5,
    borderRadius: 10,
    borderRadiusSM: 8,
    borderRadiusLG: 14,
  },
  components: {
    Layout: {
      bodyBg: '#ffffff',
      headerBg: '#ffffff',
      siderBg: '#fafafa',
    },
    Menu: {
      itemBg: 'transparent',
      itemColor: '#262626',
      itemHoverBg: '#f5f5f5',
      itemHoverColor: '#0a0a0a',
      itemSelectedBg: '#e8e8e8',
      itemSelectedColor: '#0a0a0a',
    },
  },
};

/** 在 React 首次绘制前挂载变量，让页面和挂到 body 的弹层读取同一主题。 */
export function applyWorkbenchCssVariables() {
  if (typeof document === 'undefined') return;
  const rootStyle = document.documentElement.style;
  Object.entries(workbenchCssVariables).forEach(([name, value]) => {
    rootStyle.setProperty(name, value);
  });
}
