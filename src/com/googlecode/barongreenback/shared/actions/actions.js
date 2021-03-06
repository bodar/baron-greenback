(function() {
    var actions = BGB.namespace('search.bulk.actions');
    actions.removeAllIdentifiers = function() {
        jQuery('div.actions form input').filter('[name="id"]').remove();
        jQuery('div.actions form input').filter('[name="query"]').remove();
    };

    actions.attach = function() {
        jQuery('table.results tbody').click(function(event) {
            if (event.target.nodeName === 'TD') {
                var target = jQuery(event.target);
                var parent = jQuery(target.parent('tr')[0]);
                parent.toggleClass('selected');
                BGB.search.bulk.actions.removeAllIdentifiers();
                jQuery('div.actions form').append(jQuery('table.results tbody tr.selected >td:first-child').map(function(index, el){return jQuery(el).text()}).map(function(index, text) {
                    return '<input type="hidden" name="id" value="' + text + '"/>';
                }).toArray().join(''));
                jQuery('.table-actions .message').text(jQuery('table.results tr.selected').size() + " rows are selected");
                BGB.search.allPagesSelected = false;
            }
        });
        jQuery('.selectors a.selectPage').click(function() {
            jQuery('table.results tbody tr').removeClass('selected');
            jQuery('table.results tbody td:first-child').click();
            BGB.search.allPagesSelected = false;
            return false;
        });
        jQuery('.selectors a.selectAll').click(function() {
            jQuery('.selectors a.selectPage').click();
            BGB.search.bulk.actions.removeAllIdentifiers();
            jQuery('div.actions form').append('<input type="hidden" name="query" value="' + BGB.encodeTextToHtmlEntities(jQuery('meta[name="query"]').attr('content')) + '">');
            jQuery('.table-actions .message').text('All ' + BGB.search.rowCount() +' rows are selected');
            BGB.search.allPagesSelected = true;
            return false;
        });
        jQuery('.selectors a.clearSelection').click(function() {
            jQuery('table.results tr.selected >td:first-child').click();
            BGB.search.bulk.actions.removeAllIdentifiers();
            BGB.search.allPagesSelected = false;
            return false;
        });
    };
})();

jQuery(document).ready(function() {
    BGB.search.bulk.actions.attach();
});
