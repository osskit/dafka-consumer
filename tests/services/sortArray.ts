import {sha256} from 'js-sha256';
import {sortBy} from 'lodash-es';

export const sortArray = (array: unknown[]) =>
    sortBy(
        array.map((item) => ({key: sha256(JSON.stringify(item)), value: item})),
        ({key}) => key
    ).map(({key: _, value}) => value);
