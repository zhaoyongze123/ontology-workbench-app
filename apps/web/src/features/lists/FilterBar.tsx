import React from 'react';
import styles from './FilterBar.less';

/** 统一筛选区几何与键盘搜索语义，具体字段和查询状态由页面维护。 */
const FilterBar: React.FC<React.PropsWithChildren<{ className?: string }>> = ({
  children,
  className,
}) => (
  <section
    className={[styles.filterBar, className].filter(Boolean).join(' ')}
    role="search"
    aria-label="列表筛选"
  >
    {children}
  </section>
);

export default FilterBar;
