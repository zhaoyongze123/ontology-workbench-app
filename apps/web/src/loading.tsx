import { Row, Spin } from 'antd';

export default () => {
  return (
    <Row
      style={{ width: '100%', height: '100%' }}
      justify="center"
      align="middle"
    >
      <Spin />
    </Row>
  );
};
