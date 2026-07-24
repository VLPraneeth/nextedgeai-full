import DataScoreLineItem from 'components/data-fitness/DataScoreLineItem';
import { EntityDataScore } from 'store/datascore';

export interface DataScoreLineItemsProps {
  scorecards: {
    id: string;
    card: EntityDataScore & {
      entityName: string;
      links: never[];
    };
  }[];
}

const DataScoreLineItems = ({ scorecards }: DataScoreLineItemsProps) => {
  return (
    <div className="datascore-line-items">
      <div>
        {scorecards.map(({ card, id }, idx) => (
          <DataScoreLineItem
            key={id}
            label={card.entityName}
            score={card.score}
            factors={card.factors}
            // expand the first item when we only have a few
            initialExpand={scorecards.length < 4 && idx === 0}
          />
        ))}
      </div>
    </div>
  );
};

export default DataScoreLineItems;
